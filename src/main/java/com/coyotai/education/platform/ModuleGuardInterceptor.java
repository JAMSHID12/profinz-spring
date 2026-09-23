package com.coyotai.education.platform;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces {@link RequiresModule} on every API call. This is the backend half of module
 * switching: hiding a menu is never relied on for security.
 */
@Component
public class ModuleGuardInterceptor implements HandlerInterceptor {

    private final ProjectConfigService configService;

    public ModuleGuardInterceptor(ProjectConfigService configService) {
        this.configService = configService;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (handler instanceof HandlerMethod method) {
            check(AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequiresModule.class));
            check(AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequiresModule.class));
        }
        return true;
    }

    private void check(RequiresModule annotation) {
        if (annotation == null) {
            return;
        }
        for (ModuleCode module : annotation.value()) {
            configService.requireModule(module);
        }
    }
}
