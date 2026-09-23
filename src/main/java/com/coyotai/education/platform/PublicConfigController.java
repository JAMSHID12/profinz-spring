package com.coyotai.education.platform;

import com.coyotai.education.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Safe, public view of the client configuration so the frontend can brand itself and build
 * its navigation. Contains no credentials, secrets or infrastructure details.
 */
@RestController
@RequestMapping("/api/config")
public class PublicConfigController {

    private final ProjectConfigService configService;

    public PublicConfigController(ProjectConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/public")
    public ApiResponse<PublicConfig> publicConfig() {
        ClientProperties client = configService.client();
        BrandingProperties branding = configService.branding();

        Map<String, Boolean> modules = new LinkedHashMap<>();
        Arrays.stream(ModuleCode.values())
                .forEach(module -> modules.put(key(module.name()), configService.isModuleEnabled(module)));

        Map<String, Boolean> roles = new LinkedHashMap<>();
        Arrays.stream(RoleCode.values())
                .forEach(role -> roles.put(key(role.name()), configService.isRoleEnabled(role)));

        return ApiResponse.ok(new PublicConfig(
                new PublicConfig.Client(
                        configService.getClientCode(),
                        configService.getClientName(),
                        client.getTagline(),
                        configService.getClientLogo(),
                        client.getCurrency(),
                        client.getLocale(),
                        client.getTimezone()),
                new PublicConfig.Branding(
                        branding.getPrimaryColor(),
                        branding.getLoginMessage()),
                modules,
                roles,
                new PublicConfig.Features(
                        configService.academics().getPerformance().isEnabled(),
                        configService.academics().isStudentSyllabusVisible())));
    }

    /** STUDENT_PORTAL -> studentPortal, to match JSON conventions in the frontend. */
    private String key(String enumName) {
        String[] parts = enumName.toLowerCase().split("_");
        StringBuilder result = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            result.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return result.toString();
    }

    public record PublicConfig(
            Client client,
            Branding branding,
            Map<String, Boolean> modules,
            Map<String, Boolean> roles,
            Features features
    ) {

        public record Client(String code, String name, String tagline, String logo,
                             String currency, String locale, String timezone) {
        }

        public record Branding(String primaryColor, String loginMessage) {
        }

        public record Features(boolean performance, boolean studentSyllabus) {
        }
    }
}
