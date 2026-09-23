package com.coyotai.education.security;

import com.coyotai.education.common.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Access to the signed-in user from services, without threading it through every call. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AppUserDetails> get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserDetails user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static AppUserDetails require() {
        return get().orElseThrow(() -> new UnauthorizedException("Authentication required"));
    }

    public static Long idOrNull() {
        return get().map(AppUserDetails::getId).orElse(null);
    }
}
