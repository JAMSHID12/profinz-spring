package com.coyotai.education.scheduler;

import com.coyotai.education.notification.NotificationQueueProcessor;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.whatsapp.WhatsAppProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationSchedulerTest {
    @Test void configuredCronRunsAtElevenAmIndiaTime() throws Exception {
        var environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(source -> environment.getPropertySources().addFirst(source));
        var annotation = NotificationScheduler.class.getMethod("run").getAnnotation(Scheduled.class);
        var cron = CronExpression.parse(environment.resolveRequiredPlaceholders(annotation.cron()));
        var zone = ZoneId.of(environment.resolveRequiredPlaceholders(annotation.zone()));
        var before = ZonedDateTime.of(2026, 9, 22, 10, 59, 0, 0, zone);
        var next = cron.next(before);
        assertThat(zone).isEqualTo(ZoneId.of("Asia/Kolkata"));
        assertThat(next).isEqualTo(before.withHour(11).withMinute(0));
        assertThat(cron.next(next)).isEqualTo(next.plusDays(1));
    }

    @Test void drainsAllBatchesUsingOneCutoffSoRetriesWaitForNextRun() {
        var processor = mock(NotificationQueueProcessor.class);
        var properties = mock(WhatsAppProperties.class);
        var config = mock(ProjectConfigService.class);
        when(properties.queue()).thenReturn(new WhatsAppProperties.Queue(true, 1800, 20, 3, 5));
        when(config.isModuleEnabled(ModuleCode.NOTIFICATIONS)).thenReturn(true);
        when(processor.processBatch(any(Instant.class))).thenReturn(20, 12, 0);
        new NotificationScheduler(processor, properties, config).run();
        var cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(processor, times(3)).processBatch(cutoff.capture());
        assertThat(cutoff.getAllValues()).containsOnly(cutoff.getValue());
    }

    @Test void disabledQueueDoesNotProcessMessages() {
        var processor = mock(NotificationQueueProcessor.class);
        var properties = mock(WhatsAppProperties.class);
        when(properties.queue()).thenReturn(new WhatsAppProperties.Queue(false, 1800, 20, 3, 5));
        new NotificationScheduler(processor, properties, mock(ProjectConfigService.class)).run();
        verifyNoInteractions(processor);
    }
}