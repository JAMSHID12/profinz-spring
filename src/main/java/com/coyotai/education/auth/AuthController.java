package com.coyotai.education.auth;

import com.coyotai.education.auth.AuthDtos.AuthResponse;
import com.coyotai.education.auth.AuthDtos.ChangePasswordRequest;
import com.coyotai.education.auth.AuthDtos.LoginRequest;
import com.coyotai.education.auth.AuthDtos.RefreshRequest;
import com.coyotai.education.auth.AuthDtos.SessionUser;
import com.coyotai.education.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request), "Signed in");
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    /** The signed-in user with effective roles and permissions. */
    @GetMapping("/me")
    public ApiResponse<SessionUser> me() {
        return ApiResponse.ok(authService.me());
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changeOwnPassword(request);
        return ApiResponse.ok(null, "Password changed");
    }
}
