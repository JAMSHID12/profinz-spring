package com.coyotai.education.security;

import com.coyotai.education.auth.PermissionCode;
import com.coyotai.education.platform.RequiresModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the RBAC design at build time: every API endpoint must declare the permission it
 * needs, every permission must exist, and business controllers must belong to a module.
 * A forgotten annotation or a typo in a permission name fails the build.
 */
class ApiSecurityContractTest {

    /** Endpoints that are intentionally open or only need a signed-in user. */
    private static final Set<String> EXEMPT = Set.of(
            "AuthController.login", "AuthController.refresh", "AuthController.me", "AuthController.changePassword",
            "PublicConfigController.publicConfig");

    /** Controllers that serve every module (sign-in, public branding). */
    private static final Set<String> MODULE_EXEMPT = Set.of("AuthController", "PublicConfigController");

    private static final Pattern AUTHORITY = Pattern.compile("has(?:Any)?Authority\\(([^)]*)\\)");
    private static final Pattern QUOTED = Pattern.compile("'([A-Z_]+)'");

    @Test
    @DisplayName("Every API endpoint declares a permission check")
    void everyEndpointIsProtected() throws Exception {
        List<String> unprotected = new ArrayList<>();
        for (Class<?> controller : controllers()) {
            for (Method method : handlerMethods(controller)) {
                String name = controller.getSimpleName() + "." + method.getName();
                if (!EXEMPT.contains(name) && AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class) == null) {
                    unprotected.add(name);
                }
            }
        }
        assertThat(unprotected).as("endpoints without @PreAuthorize").isEmpty();
    }

    @Test
    @DisplayName("Every permission named in @PreAuthorize exists")
    void permissionsExist() throws Exception {
        Set<String> known = Arrays.stream(PermissionCode.values()).map(Enum::name).collect(Collectors.toSet());
        List<String> unknown = new ArrayList<>();
        for (Class<?> controller : controllers()) {
            for (Method method : handlerMethods(controller)) {
                PreAuthorize rule = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
                if (rule == null) {
                    continue;
                }
                Matcher authority = AUTHORITY.matcher(rule.value());
                while (authority.find()) {
                    Matcher quoted = QUOTED.matcher(authority.group(1));
                    while (quoted.find()) {
                        if (!known.contains(quoted.group(1))) {
                            unknown.add(controller.getSimpleName() + "." + method.getName() + " -> " + quoted.group(1));
                        }
                    }
                }
            }
        }
        assertThat(unknown).as("unknown permissions").isEmpty();
    }

    @Test
    @DisplayName("Every business controller belongs to a module that can be switched off")
    void controllersBelongToModules() throws Exception {
        List<String> missing = new ArrayList<>();
        for (Class<?> controller : controllers()) {
            if (!MODULE_EXEMPT.contains(controller.getSimpleName())
                    && AnnotatedElementUtils.findMergedAnnotation(controller, RequiresModule.class) == null) {
                missing.add(controller.getSimpleName());
            }
        }
        assertThat(missing).as("controllers without @RequiresModule").isEmpty();
    }

    @Test
    @DisplayName("Student portal endpoints only use the self-service permissions")
    void studentPortalUsesOnlyMyPermissions() throws Exception {
        Class<?> portal = Class.forName("com.coyotai.education.student.portal.StudentPortalController");
        for (Method method : handlerMethods(portal)) {
            String rule = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class).value();
            Matcher quoted = QUOTED.matcher(rule);
            while (quoted.find()) {
                assertThat(quoted.group(1)).as(method.getName()).startsWith("MY_");
            }
        }
    }

    private List<Class<?>> controllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> classes = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents("com.coyotai.education")) {
            classes.add(Class.forName(candidate.getBeanClassName()));
        }
        assertThat(classes).as("controllers found").hasSizeGreaterThan(15);
        return classes;
    }

    private List<Method> handlerMethods(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
                .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
                .toList();
    }
}
