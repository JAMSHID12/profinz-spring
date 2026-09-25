package com.coyotai.education.scheduler;

import com.coyotai.education.notification.NotificationQueueProcessor;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.whatsapp.WhatsAppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Drains due notification batches using the configured daily or interval trigger. */
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

    @PostConstruct
    public void reportQueueConfiguration() {
        if (!whatsAppProperties.queue().enabled()) {
            log.warn("Notification queue is disabled (NOTIFICATION_QUEUE_ENABLED=false). "
                    + "Queued notifications will remain PENDING until it is enabled and the backend restarted.");
        } else if (!configService.isModuleEnabled(ModuleCode.NOTIFICATIONS)) {
            log.warn("Notification queue cannot run because the NOTIFICATIONS module is disabled.");
        } else {
            log.info("Notification queue is enabled; due notifications will be processed by the configured trigger.");
        }
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
            log.error("Notification queue run failed", ex);
        }
    }
}
