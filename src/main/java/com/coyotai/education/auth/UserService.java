package com.coyotai.education.auth;

import com.coyotai.education.auth.AuthDtos.UserRequest;
import com.coyotai.education.auth.AuthDtos.UserResponse;
import com.coyotai.education.audit.AuditService;
import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.common.DuplicateResourceException;
import com.coyotai.education.common.ResourceNotFoundException;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.platform.RoleCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** User accounts and their roles. Role and password changes are audited. */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProjectConfigService configService;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, ProjectConfigService configService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.configService = configService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> search(String search) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        return userRepository.search(term).stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        if (request.password() == null || request.password().isBlank()) {
            throw new BusinessRuleException("A password is required for a new user");
        }
        User user = createAccount(request.username(), request.password(), request.fullName(),
                request.email(), request.mobile(), request.roles(), false);
        user.setActive(request.active() == null || request.active());
        auditService.record("User", user.getId(), AuditService.CREATE,
                "Created user " + user.getUsername() + " with roles " + request.roles());
        return UserResponse.from(user);
    }

    /**
     * Creates a login. Also used when a student, mentor or faculty profile gets an account,
     * so every profile shares this single authentication mechanism.
     */
    @Transactional
    public User createAccount(String username, String rawPassword, String fullName, String email, String mobile,
                              Set<RoleCode> roleCodes, boolean mustChangePassword) {
        String cleanUsername = username.trim();
        if (userRepository.existsByUsernameIgnoreCase(cleanUsername)) {
            throw new DuplicateResourceException("Username is already taken: " + cleanUsername);
        }
        User user = new User();
        user.setUsername(cleanUsername);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setFullName(fullName.trim());
        user.setEmail(blankToNull(email));
        user.setMobile(blankToNull(mobile));
        user.setActive(true);
        user.setMustChangePassword(mustChangePassword);
        user.setRoles(resolveRoles(roleCodes));
        return userRepository.save(user);
    }

    @Transactional
    public UserResponse update(Long id, UserRequest request) {
        User user = getUser(id);
        String username = request.username().trim();
        if (!user.getUsername().equalsIgnoreCase(username) && userRepository.existsByUsernameIgnoreCase(username)) {
            throw new DuplicateResourceException("Username is already taken: " + username);
        }
        Set<RoleCode> before = user.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());
        boolean nextActive = request.active() == null || request.active();

        user.setUsername(username);
        user.setFullName(request.fullName().trim());
        user.setEmail(blankToNull(request.email()));
        user.setMobile(blankToNull(request.mobile()));
        user.setActive(nextActive);
        user.setRoles(resolveRoles(request.roles()));
        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        userRepository.flush();
        guardAdministratorRemains();

        if (!before.equals(request.roles())) {
            auditService.record("User", user.getId(), AuditService.UPDATE,
                    "Changed roles of " + user.getUsername() + " from " + before + " to " + request.roles());
        } else {
            auditService.record("User", user.getId(), AuditService.UPDATE, "Updated user " + user.getUsername());
        }
        return UserResponse.from(user);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User user = getUser(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true);
        auditService.record("User", user.getId(), AuditService.UPDATE, "Reset password of " + user.getUsername());
    }

    /** Refuses changes that would leave nobody able to manage users. */
    private void guardAdministratorRemains() {
        long managers = 0;
        for (Role role : roleRepository.findAllWithPermissions()) {
            boolean grantsUserManagement = role.getPermissions().stream()
                    .anyMatch(permission -> PermissionCode.USER_MANAGE.name().equals(permission.getCode()));
            if (grantsUserManagement && configService.isRoleEnabled(role.getCode())) {
                managers += userRepository.countActiveWithRole(role.getCode());
            }
        }
        if (managers == 0) {
            throw new BusinessRuleException("At least one active user must be able to manage users");
        }
    }

    private Set<Role> resolveRoles(Set<RoleCode> codes) {
        Set<Role> roles = new HashSet<>();
        for (RoleCode code : codes) {
            roles.add(roleRepository.findByCode(code)
                    .orElseThrow(() -> new BusinessRuleException("Unknown role " + code)));
        }
        return roles;
    }

    public User getUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
