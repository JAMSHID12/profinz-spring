package com.coyotai.education.scheduler;

import com.coyotai.education.notification.NotificationQueueProcessor;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.whatsapp.WhatsAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
// import java.util.concurrent.TimeUnit; // Enable with the interval annotation below.

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

    // Daily at 11:00 AM India time. Cron fields: second minute hour day month weekday.
	/*
	 * @Scheduled(cron = "${whatsapp.queue.daily-cron:0 0 11 * * *}", zone =
	 * "${whatsapp.queue.time-zone:Asia/Kolkata}")
	 */
    // Alternative: every 30 MINUTES. Comment the daily annotation before enabling this.
     @Scheduled(fixedDelayString = "${whatsapp.queue.fixed-delay-seconds:1800}",
             initialDelayString = "${whatsapp.queue.fixed-delay-seconds:1800}",
             timeUnit = TimeUnit.SECONDS)
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
