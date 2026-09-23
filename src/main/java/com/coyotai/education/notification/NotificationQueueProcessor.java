package com.coyotai.education.notification;

import com.coyotai.education.whatsapp.dto.WhatsAppSendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Drains the queue. Every external call happens here, outside business transactions, so a
 * slow or broken provider never blocks attendance, marks or payments.
 */
@Service
public class NotificationQueueProcessor {

    private static final Logger log = LoggerFactory.getLogger(NotificationQueueProcessor.class);

    private final NotificationService notificationService;
    private final Map<NotificationChannel, NotificationSender> senders = new EnumMap<>(NotificationChannel.class);

    public NotificationQueueProcessor(NotificationService notificationService, List<NotificationSender> senders) {
        this.notificationService = notificationService;
        senders.forEach(sender -> this.senders.put(sender.channel(), sender));
    }

    /** @return the number of messages processed in this run. */
    public int processBatch(Instant scheduledThrough) {
        List<Notification> batch = notificationService.claimDueBatch(scheduledThrough);
        if (batch.isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (Notification notification : batch) {
            if (dispatch(notification)) {
                sent++;
            }
        }
        log.info("Notification queue run: {} sent, {} failed", sent, batch.size() - sent);
        return batch.size();
    }

    private boolean dispatch(Notification notification) {
        NotificationSender sender = senders.get(notification.getChannel());
        if (sender == null) {
            notificationService.markFailed(notification.getId(),
                    notification.getChannel() + " delivery is not configured for this deployment");
            return false;
        }
        try {
            WhatsAppSendResult result = sender.send(notification);
            if (result.success()) {
                notificationService.markSent(notification.getId());
                return true;
            }
            notificationService.markFailed(notification.getId(), result.errorMessage());
            return false;
        } catch (RuntimeException ex) {
            // One bad message must never stop the queue.
            log.warn("Notification {} could not be processed: {}", notification.getId(), ex.getMessage());
            notificationService.markFailed(notification.getId(), ex.getMessage());
            return false;
        }
    }
}
