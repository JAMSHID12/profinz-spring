package com.coyotai.education.auth;

import com.coyotai.education.auth.AuthDtos.AuthResponse;
import com.coyotai.education.auth.AuthDtos.ChangePasswordRequest;
import com.coyotai.education.auth.AuthDtos.LoginRequest;
import com.coyotai.education.auth.AuthDtos.SessionUser;
import com.coyotai.education.audit.AuditService;
import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.common.UnauthorizedException;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.security.AppUserDetails;
import com.coyotai.education.security.AppUserDetailsService;
import com.coyotai.education.security.CurrentUser;
import com.coyotai.education.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** One sign-in path for every role - staff and students use the same secure mechanism. */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final ProjectConfigService configService;
    private final AuditService auditService;

    public AuthService(AuthenticationManager authenticationManager, AppUserDetailsService userDetailsService,
                       UserRepository userRepository, JwtService jwtService, PasswordEncoder passwordEncoder,
                       ProjectConfigService configService, AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.configService = configService;
        this.auditService = auditService;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username().trim(), request.password()));
        AppUserDetails user = userDetailsService.loadUserByUsername(request.username().trim());
        requireEnabledRole(user);
        userRepository.findById(user.getId()).ifPresent(entity -> entity.setLastLoginAt(Instant.now()));
        return buildResponse(user);
    }

    /**
     * Exchanges a refresh token for a new pair. The user is reloaded, so a deactivated account
     * or a role switched off in configuration cannot keep refreshing.
     */
    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        String username = jwtService.extractUsername(refreshToken, true, configService.getClientCode());
        if (username == null) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
        AppUserDetails user;
        try {
            user = userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException ex) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
        if (!user.isEnabled()) {
            throw new DisabledException("This account is inactive");
        }
        requireEnabledRole(user);
        return buildResponse(user);
    }

    public SessionUser me() {
        return SessionUser.from(CurrentUser.require());
    }

    @Transactional
    public void changeOwnPassword(ChangePasswordRequest request) {
        AppUserDetails current = CurrentUser.require();
        User user = userRepository.findById(current.getId())
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessRuleException("The current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessRuleException("The new password must be different from the current one");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        auditService.record("User", user.getId(), AuditService.UPDATE, "Changed own password");
    }

    private void requireEnabledRole(AppUserDetails user) {
        if (user.getEnabledRoles().isEmpty()) {
            throw new UnauthorizedException("Your role is not enabled for " + configService.getClientName());
        }
    }

    private AuthResponse buildResponse(AppUserDetails user) {
        String clientCode = configService.getClientCode();
        return new AuthResponse(
                jwtService.generateAccessToken(user.getUsername(), clientCode),
                jwtService.generateRefreshToken(user.getUsername(), clientCode),
                jwtService.accessTokenSeconds(),
                SessionUser.from(user));
    }
}
