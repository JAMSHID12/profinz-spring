package com.coyotai.education.platform;

import com.coyotai.education.common.ModuleDisabledException;
import com.coyotai.education.notification.NotificationChannel;
import com.coyotai.education.notification.NotificationEvent;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The single place the rest of the code asks "is this role/module on?", "who is the client?"
 * and "what day is it for this client?". Nothing else should read {@code project.*} directly.
 */
@Service
public class ProjectConfigService {

    private final ProjectProperties properties;
    private final ZoneId zoneId;

    public ProjectConfigService(ProjectProperties properties) {
        this.properties = properties;
        String timezone = properties.getClient().getTimezone();
        this.zoneId = (timezone == null || timezone.isBlank()) ? ZoneId.of("UTC") : ZoneId.of(timezone);
    }

    public boolean isModuleEnabled(ModuleCode module) {
        ProjectProperties.Toggle toggle = properties.getModule().get(module);
        return toggle != null && toggle.isEnabled();
    }

    public boolean isRoleEnabled(RoleCode role) {
        ProjectProperties.Toggle toggle = properties.getRole().get(role);
        return toggle != null && toggle.isEnabled();
    }

    public Set<ModuleCode> enabledModules() {
        Set<ModuleCode> enabled = EnumSet.noneOf(ModuleCode.class);
        Arrays.stream(ModuleCode.values()).filter(this::isModuleEnabled).forEach(enabled::add);
        return enabled;
    }

    public Set<RoleCode> enabledRoles() {
        Set<RoleCode> enabled = EnumSet.noneOf(RoleCode.class);
        Arrays.stream(RoleCode.values()).filter(this::isRoleEnabled).forEach(enabled::add);
        return enabled;
    }

    /** Throws when the module is switched off, so the caller never reaches disabled logic. */
    public void requireModule(ModuleCode module) {
        if (!isModuleEnabled(module)) {
            throw new ModuleDisabledException(module);
        }
    }

    public String getClientCode() {
        return properties.getClient().getCode();
    }

    public String getClientName() {
        String name = properties.getClient().getName();
        return (name == null || name.isBlank()) ? getClientCode() : name;
    }

    public String getClientLogo() {
        return properties.getBranding().getLogo();
    }

    public ClientProperties client() {
        return properties.getClient();
    }

    public BrandingProperties branding() {
        return properties.getBranding();
    }

    public ProjectProperties.AcademicsProperties academics() {
        return properties.getAcademics();
    }

    public ProjectProperties.FeeProperties fees() {
        return properties.getFees();
    }

    public ProjectProperties.StudentProperties student() {
        return properties.getStudent();
    }

    public ProjectProperties.BootstrapProperties bootstrap() {
        return properties.getBootstrap();
    }

    public boolean isChannelEnabled(NotificationChannel channel) {
        return Boolean.TRUE.equals(properties.getNotifications().getChannels().get(channel));
    }

    /** Enabled channels configured for an event, in configuration order. */
    public List<NotificationChannel> channelsFor(NotificationEvent event) {
        List<NotificationChannel> configured = properties.getNotifications().getEvents().get(event);
        if (configured == null) {
            return List.of();
        }
        return configured.stream().filter(this::isChannelEnabled).distinct().toList();
    }

    public ZoneId zoneId() {
        return zoneId;
    }

    /** "Today" in the client's timezone - never the server's. */
    public LocalDate today() {
        return LocalDate.now(zoneId);
    }

    public LocalTime nowTime() {
        return LocalTime.now(zoneId);
    }

    /** Receipt prefix, defaulting to the client code so no client name is baked into code. */
    public String receiptPrefix() {
        String prefix = properties.getFees().getReceiptPrefix();
        return (prefix == null || prefix.isBlank()) ? getClientCode() : prefix;
    }
}
