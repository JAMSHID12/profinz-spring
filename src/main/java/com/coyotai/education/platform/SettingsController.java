package com.coyotai.education.platform;

import com.coyotai.education.common.ApiResponse;
import com.coyotai.education.notification.NotificationChannel;
import com.coyotai.education.notification.NotificationEvent;
import com.coyotai.education.whatsapp.WhatsAppProperties;
import com.coyotai.education.whatsapp.WhatsAppService;
import org.flywaydb.core.Flyway;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The effective configuration of this deployment, for administrators. Read-only (it comes
 * from application.yml / environment variables) and free of secrets: tokens and passwords
 * are never returned, not even masked.
 */
@RestController
@RequestMapping("/api/settings")
@RequiresModule(ModuleCode.ADMINISTRATION)
public class SettingsController {

    private final ProjectConfigService configService;
    private final WhatsAppProperties whatsAppProperties;
    private final WhatsAppService whatsAppService;
    private final Flyway flyway;

    public SettingsController(ProjectConfigService configService, WhatsAppProperties whatsAppProperties,
                              WhatsAppService whatsAppService, Flyway flyway) {
        this.configService = configService;
        this.whatsAppProperties = whatsAppProperties;
        this.whatsAppService = whatsAppService;
        this.flyway = flyway;
    }

    public record Settings(
            Map<String, String> client,
            Map<String, String> branding,
            Map<String, Boolean> modules,
            Map<String, Boolean> roles,
            Map<String, Object> academics,
            Map<String, Boolean> channels,
            Map<String, List<NotificationChannel>> events,
            Map<String, Object> whatsapp,
            Map<String, Object> fees,
            Map<String, Object> database
    ) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_VIEW')")
    public ApiResponse<Settings> settings() {
        ClientProperties client = configService.client();
        BrandingProperties branding = configService.branding();

        Map<String, Boolean> modules = new LinkedHashMap<>();
        Arrays.stream(ModuleCode.values()).forEach(m -> modules.put(m.name(), configService.isModuleEnabled(m)));
        Map<String, Boolean> roles = new LinkedHashMap<>();
        Arrays.stream(RoleCode.values()).forEach(r -> roles.put(r.name(), configService.isRoleEnabled(r)));
        Map<String, Boolean> channels = new LinkedHashMap<>();
        Arrays.stream(NotificationChannel.values()).forEach(c -> channels.put(c.name(), configService.isChannelEnabled(c)));
        Map<String, List<NotificationChannel>> events = new LinkedHashMap<>();
        Arrays.stream(NotificationEvent.values()).forEach(e -> events.put(e.name(), configService.channelsFor(e)));

        Map<String, String> templates = new LinkedHashMap<>();
        Arrays.stream(NotificationEvent.values()).forEach(e -> templates.put(e.name(), whatsAppProperties.templateName(e)));
        Map<String, Object> whatsapp = new LinkedHashMap<>();
        whatsapp.put("mode", whatsAppService.mode());
        whatsapp.put("enabledFlag", whatsAppProperties.enabled());
        whatsapp.put("provider", whatsAppProperties.provider());
        whatsapp.put("baseUrl", whatsAppProperties.wabi().baseUrl());
        if (whatsAppProperties.usesWabi()) {
            whatsapp.put("event", whatsAppProperties.wabi().event());
            whatsapp.put("apiKeyConfigured", notBlank(whatsAppProperties.wabi().apiKey()));
        }
        whatsapp.put("templates", templates);
        whatsapp.put("queueIntervalSeconds", whatsAppProperties.queue().fixedDelaySeconds());
        whatsapp.put("maxRetries", whatsAppProperties.queue().maxRetries());

        Map<String, Object> academics = new LinkedHashMap<>();
        academics.put("performanceEnabled", configService.academics().getPerformance().isEnabled());
        academics.put("weights", configService.academics().getPerformance().getWeights());
        academics.put("grades", configService.academics().getPerformance().getGrades());
        academics.put("studentSyllabusVisible", configService.academics().isStudentSyllabusVisible());

        Map<String, Object> fees = new LinkedHashMap<>();
        fees.put("receiptPrefix", configService.receiptPrefix());
        fees.put("remindersEnabled", configService.fees().getReminder().isEnabled());
        fees.put("reminderCron", configService.fees().getReminder().getCron());
        fees.put("reminderOffsetDays", configService.fees().getReminder().getOffsetDays());

        Map<String, Object> database = new LinkedHashMap<>();
        var info = flyway.info().current();
        database.put("migrationVersion", info == null ? null : info.getVersion().getVersion());
        database.put("migrationDescription", info == null ? null : info.getDescription());
        database.put("pendingMigrations", flyway.info().pending().length);

        return ApiResponse.ok(new Settings(
                ordered("code", client.getCode(), "name", configService.getClientName(), "tagline", client.getTagline(),
                        "timezone", client.getTimezone(), "currency", client.getCurrency(), "locale", client.getLocale()),
                ordered("logo", branding.getLogo(), "primaryColor", branding.getPrimaryColor(),
                        "loginMessage", branding.getLoginMessage()),
                modules, roles, academics, channels, events, whatsapp, fees, database));
    }

    private Map<String, String> ordered(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
