package com.coyotai.education.scheduler;

import com.coyotai.education.notification.NotificationQueueProcessor;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.whatsapp.WhatsAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Drains due notification batches at the configured daily time and timezone. */
@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final NotificationQueueProcessor processor;
    private final WhatsAppProperties whatsAppProperties;
    private final ProjectConfigService configService;

    public NotificationScheduler(NotificationQueueProcessor processor, WhatsAppProperties whatsAppProperties,
                                 ProjectConfigService configService) {
        this.processor = processor;
        this.whatsAppProperties = whatsAppProperties;
        this.configService = configService;
    }

    // Trigger selection lives in NotificationScheduleConfiguration.
    public void run() {
        if (!whatsAppProperties.queue().enabled() || !configService.isModuleEnabled(ModuleCode.NOTIFICATIONS)) {
            return;
        }
        try {
            Instant scheduledThrough = Instant.now();
            while (processor.processBatch(scheduledThrough) > 0) {
                // Continue through due batches instead of limiting the daily run to one batch.
            }
        } catch (RuntimeException ex) {
            log.error("Notification queue run failed: {}", ex.getMessage());
        }
    }
}
