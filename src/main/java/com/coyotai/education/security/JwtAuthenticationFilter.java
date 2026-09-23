package com.coyotai.education.security;

import com.coyotai.education.platform.ProjectConfigService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;
    private final ProjectConfigService configService;

    public JwtAuthenticationFilter(JwtService jwtService, AppUserDetailsService userDetailsService,
                                   ProjectConfigService configService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.configService = configService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String username = jwtService.extractUsername(
                    header.substring(BEARER_PREFIX.length()), false, configService.getClientCode());
            if (username != null) {
                try {
                    AppUserDetails user = userDetailsService.loadUserByUsername(username);
                    // Inactive users and users whose every role is switched off stay anonymous.
                    if (user.isEnabled() && !user.getEnabledRoles().isEmpty()) {
                        var authentication = new UsernamePasswordAuthenticationToken(
                                user, null, user.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (UsernameNotFoundException ignored) {
                    // Token references a deleted user - the request continues unauthenticated.
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
