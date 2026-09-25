package com.coyotai.education.notification;

import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.student.Student;
import com.coyotai.education.student.ParentContact;
import com.coyotai.education.whatsapp.WhatsAppProperties;
import com.coyotai.education.auth.User;
import com.coyotai.education.util.JsonUtils;
import com.coyotai.education.whatsapp.dto.NotificationPayload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AttendanceNotificationTest {
    @Test void absentWhatsAppPayloadIsPreservedWithoutProviderKeyAndSeparateFromInbox() {
        var repository = mock(NotificationRepository.class);
        var config = mock(ProjectConfigService.class);
        when(config.isModuleEnabled(ModuleCode.NOTIFICATIONS)).thenReturn(true);
        when(config.channelsFor(NotificationEvent.STUDENT_ABSENT))
                .thenReturn(List.of(NotificationChannel.WHATSAPP, NotificationChannel.IN_APP));
        when(config.getClientName()).thenReturn("PROFINZ");
        var properties = new WhatsAppProperties(true, WhatsAppProperties.Provider.WABI,
                new WhatsAppProperties.Wabi("https://example.invalid", "late-key",
                        NotificationEvent.STUDENT_LATE, "parent-consent"),
                "en", 10, 20, Map.of(NotificationEvent.STUDENT_ABSENT, "student_absent"),
                new WhatsAppProperties.Queue(true, 30, 20, 3, 5));
        var parent = new ParentContact();
        parent.setName("Sainudheen");
        parent.setPhoneNumber("+919876543210");
        parent.setWhatsappOptIn(true);
        var user = mock(User.class);
        when(user.isActive()).thenReturn(true);
        when(user.getId()).thenReturn(42L);
        var student = new Student();
        student.setParent(parent);
        student.setUser(user);
        var content = new NotificationMessageFactory(config).absent(
                "Sainudheen", "Mazin", "CMA India Batch 1", LocalDate.of(2026, 9, 25));

        var result = new NotificationService(repository, config, properties)
                .publish(NotificationEvent.STUDENT_ABSENT, student, content);

        assertThat(properties.supportsEvent(NotificationEvent.STUDENT_ABSENT)).isFalse();
        assertThat(result.queued()).isEqualTo(2);
        assertThat(result.skipped()).isZero();
        var rows = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(2)).save(rows.capture());
        var whatsapp = rows.getAllValues().get(0);
        assertThat(whatsapp.getChannel()).isEqualTo(NotificationChannel.WHATSAPP);
        assertThat(whatsapp.getTemplateName()).isEqualTo("student_absent");
        assertThat(whatsapp.getDestination()).isEqualTo("+919876543210");
        var payload = JsonUtils.fromJson(whatsapp.getMessagePayload(), NotificationPayload.class);
        assertThat(payload.templateName()).isEqualTo("student_absent");
        assertThat(payload.languageCode()).isEqualTo("en");
        assertThat(payload.parameters()).containsExactly(
                "Sainudheen", "Mazin", "CMA India Batch 1", "25 Sep 2026", "PROFINZ");
        assertThat(payload.preview()).isEqualTo(content.text());
        assertThat(rows.getAllValues().get(1).getChannel()).isEqualTo(NotificationChannel.IN_APP);
    }

    @Test void lateMessagesWaitTwoMinutesButAbsentMessagesAreImmediatelyEligible() {
        var repository = mock(NotificationRepository.class);
        var config = mock(ProjectConfigService.class);
        var properties = mock(WhatsAppProperties.class);
        when(config.isModuleEnabled(ModuleCode.NOTIFICATIONS)).thenReturn(true);
        when(config.channelsFor(any())).thenReturn(List.of(NotificationChannel.WHATSAPP));
        when(properties.supportsEvent(any())).thenReturn(true);
        when(properties.templateName(any())).thenReturn("template");
        var parent = new ParentContact();
        parent.setName("Parent"); parent.setPhoneNumber("+919876543210"); parent.setWhatsappOptIn(true);
        var student = new Student(); student.setParent(parent);
        var service = new NotificationService(repository, config, properties, 120);
        var content = new NotificationContent("Attendance", List.of("Parent", "Student", "Class", "24 Sep 2026", "Centre"), "preview");
        service.publish(NotificationEvent.STUDENT_LATE, student, content);
        service.publish(NotificationEvent.STUDENT_ABSENT, student, content);
        var rows = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(2)).save(rows.capture());
        var late = rows.getAllValues().get(0);
        var absent = rows.getAllValues().get(1);
        assertThat(Duration.between(late.getCreatedAt(), late.getScheduledAt())).isEqualTo(Duration.ofSeconds(120));
        assertThat(absent.getScheduledAt()).isEqualTo(absent.getCreatedAt());
    }

    @Test void absentContentMatchesApprovedTemplateAndParameterOrder() {
        var config = mock(ProjectConfigService.class);
        when(config.getClientName()).thenReturn("PROFINZ");
        var content = new NotificationMessageFactory(config).absent("Parent", "Student", "Class", LocalDate.of(2026,9,24));
        assertThat(content.parameters()).containsExactly("Parent", "Student", "Class", "24 Sep 2026", "PROFINZ");
        assertThat(content.text()).isEqualTo("Dear Parent,\n\nThis is an attendance update from PROFINZ.\n\nStudent was marked absent from Class on 24 Sep 2026.\n\nIf you believe this record is incorrect, please contact the centre office.\n\nThank you.");
    }
}
