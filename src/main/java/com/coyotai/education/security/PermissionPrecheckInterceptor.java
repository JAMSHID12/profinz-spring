package com.coyotai.education.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks an endpoint's {@code @PreAuthorize} rule before Spring reads and validates the request
 * body. Without this, a caller lacking the permission would get a 400 listing validation errors
 * - leaking how the endpoint works - instead of a plain 403. The real method-security check
 * still runs afterwards; this only moves the refusal earlier.
 */
@Component
public class PermissionPrecheckInterceptor implements HandlerInterceptor {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final Map<String, Expression> cache = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        PreAuthorize rule = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), PreAuthorize.class);
        if (rule == null) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return true;
        }
        Expression expression = cache.computeIfAbsent(rule.value(), parser::parseExpression);
        StandardEvaluationContext context = new StandardEvaluationContext(new SecurityExpressionRoot(authentication) {
        });
        Boolean allowed;
        try {
            allowed = expression.getValue(context, Boolean.class);
        } catch (RuntimeException ex) {
            // Expressions that need method arguments cannot be pre-checked; method security decides.
            return true;
        }
        if (!Boolean.TRUE.equals(allowed)) {
            throw new AccessDeniedException("Access denied");
        }
        return true;
    }
}
