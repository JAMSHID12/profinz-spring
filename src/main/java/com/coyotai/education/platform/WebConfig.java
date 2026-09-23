package com.coyotai.education.platform;

import com.coyotai.education.security.PermissionPrecheckInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ModuleGuardInterceptor moduleGuardInterceptor;
    private final PermissionPrecheckInterceptor permissionPrecheckInterceptor;

    public WebConfig(ModuleGuardInterceptor moduleGuardInterceptor,
                     PermissionPrecheckInterceptor permissionPrecheckInterceptor) {
        this.moduleGuardInterceptor = moduleGuardInterceptor;
        this.permissionPrecheckInterceptor = permissionPrecheckInterceptor;
    }

    /** Module first (is the area switched on?), then permission (may this user use it?). */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(moduleGuardInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(permissionPrecheckInterceptor).addPathPatterns("/api/**");
    }
}
