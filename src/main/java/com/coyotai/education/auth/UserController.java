package com.coyotai.education.auth;

import com.coyotai.education.auth.AuthDtos.PermissionResponse;
import com.coyotai.education.auth.AuthDtos.ResetPasswordRequest;
import com.coyotai.education.auth.AuthDtos.RolePermissionsRequest;
import com.coyotai.education.auth.AuthDtos.RoleResponse;
import com.coyotai.education.auth.AuthDtos.UserRequest;
import com.coyotai.education.auth.AuthDtos.UserResponse;
import com.coyotai.education.common.ApiResponse;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.RequiresModule;
import com.coyotai.education.platform.RoleCode;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** User accounts, roles and role permissions. */
@RestController
@RequestMapping("/api")
@RequiresModule(ModuleCode.ADMINISTRATION)
public class UserController {

    private final UserService userService;
    private final RbacService rbacService;

    public UserController(UserService userService, RbacService rbacService) {
        this.userService = userService;
        this.rbacService = rbacService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public ApiResponse<List<UserResponse>> users(@RequestParam(required = false) String search) {
        return ApiResponse.ok(userService.search(search));
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ApiResponse<UserResponse> create(@Valid @RequestBody UserRequest request) {
        return ApiResponse.ok(userService.create(request), "User created");
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ApiResponse<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return ApiResponse.ok(userService.update(id, request), "User updated");
    }

    @PutMapping("/users/{id}/password")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request.newPassword());
        return ApiResponse.ok(null, "Password reset. The user must change it at next sign-in.");
    }

    @GetMapping("/rbac/roles")
    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    public ApiResponse<List<RoleResponse>> roles() {
        return ApiResponse.ok(rbacService.roles());
    }

    @GetMapping("/rbac/permissions")
    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    public ApiResponse<List<PermissionResponse>> permissions() {
        return ApiResponse.ok(rbacService.permissions());
    }

    @PutMapping("/rbac/roles/{code}/permissions")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ApiResponse<RoleResponse> updateRolePermissions(@PathVariable RoleCode code,
                                                           @Valid @RequestBody RolePermissionsRequest request) {
        return ApiResponse.ok(rbacService.updatePermissions(code, request.permissions()), "Role permissions updated");
    }
}
