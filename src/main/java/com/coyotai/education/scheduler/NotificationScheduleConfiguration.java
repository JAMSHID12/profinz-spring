package com.coyotai.education.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.concurrent.TimeUnit;

/** Only one scheduler is registered; changing cadence requires configuration, not Java edits. */
@Configuration(proxyBeanMethods = false)
public class NotificationScheduleConfiguration {
    public NotificationScheduleConfiguration(@Value("${whatsapp.queue.schedule-mode:interval}") String mode) {
        if (!"daily".equals(mode) && !"interval".equals(mode))
            throw new IllegalArgumentException("NOTIFICATION_SCHEDULE_MODE must be daily or interval");
    }

    @Bean
    @ConditionalOnProperty(name = "whatsapp.queue.schedule-mode", havingValue = "daily")
    DailySchedule dailyNotificationSchedule(NotificationScheduler scheduler) {
        return new DailySchedule(scheduler);
    }

    @Bean
    @ConditionalOnProperty(name = "whatsapp.queue.schedule-mode", havingValue = "interval", matchIfMissing = true)
    IntervalSchedule intervalNotificationSchedule(NotificationScheduler scheduler) {
        return new IntervalSchedule(scheduler);
    }

    public record DailySchedule(NotificationScheduler scheduler) {
        @Scheduled(cron = "${whatsapp.queue.daily-cron:0 0 11 * * *}",
                zone = "${whatsapp.queue.time-zone:Asia/Kolkata}")
        public void run() { scheduler.run(); }
    }

    public record IntervalSchedule(NotificationScheduler scheduler) {
        @Scheduled(fixedDelayString = "${whatsapp.queue.fixed-delay-seconds:30}",
                initialDelayString = "${whatsapp.queue.fixed-delay-seconds:30}", timeUnit = TimeUnit.SECONDS)
        public void run() { scheduler.run(); }
    }
}
