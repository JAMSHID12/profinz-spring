package com.coyotai.education.whatsapp;

import com.coyotai.education.notification.NotificationMessageFactory;
import com.coyotai.education.platform.ProjectConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WabiConfigurationTest {
    @Test void applicationYamlLoadsWithJpaAndWabiSettingsAtCorrectPaths() throws Exception {
        var sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        assertThat(sources).hasSize(1);
        var source = sources.get(0);
        assertThat(source.getProperty("spring.jpa.show-sql")).isEqualTo(true);
        assertThat(source.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone")).isEqualTo("UTC");
        assertThat(source.getProperty("whatsapp.provider")).isEqualTo("${WHATSAPP_PROVIDER:WABI}");
        assertThat(source.getProperty("whatsapp.wabi.api-key")).isEqualTo("${WABI_API_KEY:}");
        assertThat(source.getProperty("whatsapp.wabi.opt-in-source")).isEqualTo("${WABI_OPT_IN_SOURCE:profinz-parent-consent}");
    }

    @Test void attendanceParametersMatchApprovedWabiTemplateOrder() {
        var config = mock(ProjectConfigService.class);
        when(config.getClientName()).thenReturn("PROFINZ");
        var message = new NotificationMessageFactory(config).late(
                "Test Parent", "Test Student", "Class 10 / Batch A", LocalDate.of(2026, 9, 22));
        assertThat(message.parameters()).containsExactly(
                "Test Parent", "Test Student", "Class 10 / Batch A", "22 Sep 2026", "PROFINZ");
    }
}