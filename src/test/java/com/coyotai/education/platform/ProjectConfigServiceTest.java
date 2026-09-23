package com.coyotai.education.platform;

import com.coyotai.education.common.ModuleDisabledException;
import com.coyotai.education.notification.NotificationChannel;
import com.coyotai.education.notification.NotificationEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The same code must behave differently for differently configured clients. */
class ProjectConfigServiceTest {

    private ProjectProperties client(boolean directors, boolean sales, boolean accounts) {
        ProjectProperties properties = new ProjectProperties();
        properties.getClient().setCode("PROFINZ");
        properties.getClient().setName("PROFINZ");
        properties.getClient().setTimezone("Asia/Kolkata");
        properties.getRole().put(RoleCode.DIRECTORS, toggle(directors));
        properties.getRole().put(RoleCode.SALES, toggle(sales));
        properties.getRole().put(RoleCode.ACCOUNTS, toggle(accounts));
        properties.getRole().put(RoleCode.ACADEMICS, toggle(true));
        properties.getRole().put(RoleCode.STUDENTS, toggle(true));
        properties.getRole().put(RoleCode.MENTORS, toggle(true));
        properties.getRole().put(RoleCode.FACULTY, toggle(true));
        properties.getModule().put(ModuleCode.ACADEMICS, toggle(true));
        properties.getModule().put(ModuleCode.FEES, toggle(false));
        return properties;
    }

    private ProjectProperties.Toggle toggle(boolean enabled) {
        ProjectProperties.Toggle toggle = new ProjectProperties.Toggle();
        toggle.setEnabled(enabled);
        return toggle;
    }

    @Test
    @DisplayName("Spec configuration 1: directors, sales and accounts unavailable")
    void firstClient() {
        ProjectConfigService config = new ProjectConfigService(client(false, false, false));
        assertThat(config.enabledRoles()).containsExactlyInAnyOrder(
                RoleCode.ACADEMICS, RoleCode.STUDENTS, RoleCode.MENTORS, RoleCode.FACULTY);
        assertThat(config.isRoleEnabled(RoleCode.DIRECTORS)).isFalse();
    }

    @Test
    @DisplayName("Spec configuration 2: the same code with every role available")
    void secondClient() {
        ProjectConfigService config = new ProjectConfigService(client(true, true, true));
        assertThat(config.isRoleEnabled(RoleCode.DIRECTORS)).isTrue();
        assertThat(config.isRoleEnabled(RoleCode.SALES)).isTrue();
        assertThat(config.isRoleEnabled(RoleCode.ACCOUNTS)).isTrue();
    }

    @Test
    @DisplayName("A role or module missing from configuration counts as disabled")
    void missingMeansDisabled() {
        ProjectConfigService config = new ProjectConfigService(client(false, false, false));
        assertThat(config.isRoleEnabled(RoleCode.ADMINISTRATIVE)).isFalse();
        assertThat(config.isModuleEnabled(ModuleCode.REPORTS)).isFalse();
    }

    @Test
    @DisplayName("A disabled module is refused")
    void disabledModuleRefused() {
        ProjectConfigService config = new ProjectConfigService(client(false, false, false));
        assertThatThrownBy(() -> config.requireModule(ModuleCode.FEES))
                .isInstanceOf(ModuleDisabledException.class)
                .hasMessageContaining("Fees module is not enabled");
    }

    @Test
    @DisplayName("Only enabled channels are used for an event")
    void channels() {
        ProjectProperties properties = client(false, false, false);
        properties.getNotifications().getChannels().put(NotificationChannel.WHATSAPP, true);
        properties.getNotifications().getChannels().put(NotificationChannel.SMS, false);
        properties.getNotifications().getEvents().put(NotificationEvent.STUDENT_ABSENT,
                List.of(NotificationChannel.WHATSAPP, NotificationChannel.SMS));
        ProjectConfigService config = new ProjectConfigService(properties);
        assertThat(config.channelsFor(NotificationEvent.STUDENT_ABSENT)).containsExactly(NotificationChannel.WHATSAPP);
        assertThat(config.channelsFor(NotificationEvent.FEE_DUE)).isEmpty();
    }

    @Test
    @DisplayName("Receipt prefix defaults to the client code - no client name in code")
    void receiptPrefix() {
        ProjectProperties properties = client(false, false, false);
        assertThat(new ProjectConfigService(properties).receiptPrefix()).isEqualTo("PROFINZ");
        properties.getFees().setReceiptPrefix("PFZ");
        assertThat(new ProjectConfigService(properties).receiptPrefix()).isEqualTo("PFZ");
    }
}
