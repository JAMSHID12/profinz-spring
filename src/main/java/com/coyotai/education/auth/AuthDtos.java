package com.coyotai.education.auth;

import com.coyotai.education.platform.RoleCode;
import com.coyotai.education.security.AppUserDetails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Request and response shapes for authentication, users and roles. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Username is required") String username,
            @NotBlank(message = "Password is required") String password
    ) {
    }

    public record RefreshRequest(@NotBlank(message = "Refresh token is required") String refreshToken) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required") String currentPassword,
            @NotBlank(message = "New password is required")
            @Size(min = 8, max = 72, message = "The new password must be 8-72 characters")
            String newPassword
    ) {
    }

    public record AuthResponse(String accessToken, String refreshToken, long expiresInSeconds, SessionUser user) {
    }

    /**
     * Everything the frontend needs to render the right portal: enabled roles, effective
     * permissions and the linked profile ids.
     */
    public record SessionUser(
            Long id,
            String username,
            String fullName,
            List<RoleCode> roles,
            List<String> permissions,
            Long studentId,
            Long mentorId,
            Long facultyId,
            boolean mustChangePassword,
            String portal
    ) {

        public static SessionUser from(AppUserDetails user) {
            return new SessionUser(
                    user.getId(),
                    user.getUsername(),
                    user.getFullName(),
                    user.getEnabledRoles().stream().sorted().toList(),
                    user.getPermissions().stream().sorted().toList(),
                    user.getStudentId(),
                    user.getMentorId(),
                    user.getFacultyId(),
                    user.isMustChangePassword(),
                    portalOf(user.getEnabledRoles()));
        }

        /** Which home screen the user lands on: STAFF, MENTOR, FACULTY or STUDENT. */
        static String portalOf(Set<RoleCode> roles) {
            if (roles.stream().anyMatch(role -> role.scope() == RoleCode.Scope.GLOBAL)) {
                return "STAFF";
            }
            if (roles.contains(RoleCode.MENTORS)) {
                return "MENTOR";
            }
            if (roles.contains(RoleCode.FACULTY)) {
                return "FACULTY";
            }
            return "STUDENT";
        }
    }

    public record UserRequest(
            @NotBlank(message = "Username is required")
            @Size(min = 3, max = 60, message = "Username must be 3-60 characters")
            @Pattern(regexp = "^[A-Za-z0-9._@-]+$", message = "Username may contain letters, digits and . _ @ - only")
            String username,
            @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
            String password,
            @NotBlank(message = "Full name is required") @Size(max = 150) String fullName,
            @Email(message = "Enter a valid e-mail address") @Size(max = 150) String email,
            @Pattern(regexp = "^$|^\\+?[0-9]{7,15}$", message = "Enter a valid mobile number") String mobile,
            @NotEmpty(message = "Select at least one role") Set<RoleCode> roles,
            Boolean active
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "New password is required")
            @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
            String newPassword
    ) {
    }

    public record UserResponse(Long id, String username, String fullName, String email, String mobile,
                               List<RoleCode> roles, boolean active, boolean mustChangePassword,
                               Instant lastLoginAt, Instant createdAt) {

        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                    user.getMobile(), user.getRoles().stream().map(Role::getCode).sorted().toList(),
                    user.isActive(), user.isMustChangePassword(), user.getLastLoginAt(), user.getCreatedAt());
        }
    }

    public record RoleResponse(RoleCode code, String name, String description, boolean enabled,
                               long activeUsers, List<String> permissions) {
    }

    public record PermissionResponse(String code, String name, String module) {
    }

    public record RolePermissionsRequest(@NotEmpty(message = "Select at least one permission") Set<String> permissions) {
    }
}
