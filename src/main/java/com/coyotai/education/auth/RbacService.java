package com.coyotai.education.auth;

import com.coyotai.education.auth.AuthDtos.PermissionResponse;
import com.coyotai.education.auth.AuthDtos.RoleResponse;
import com.coyotai.education.audit.AuditService;
import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.common.ResourceNotFoundException;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.platform.RoleCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Role -> permission grants are data, so each client deployment can tailor them without code.
 * Guard rails stop the dangerous edits: giving students staff powers, letting anyone but
 * mentors and faculty take attendance, and locking everyone out of administration.
 */
@Service
public class RbacService {

    private static final String STUDENT_PORTAL_MODULE = "STUDENT_PORTAL";
    private static final List<String> ATTENDANCE_TAKING = List.of(
            PermissionCode.ATTENDANCE_CREATE.name(), PermissionCode.ATTENDANCE_UPDATE.name());

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final ProjectConfigService configService;
    private final AuditService auditService;

    public RbacService(RoleRepository roleRepository, PermissionRepository permissionRepository,
                       UserRepository userRepository, ProjectConfigService configService,
                       AuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.configService = configService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> roles() {
        return roleRepository.findAllWithPermissions().stream()
                .map(role -> new RoleResponse(
                        role.getCode(),
                        role.getName(),
                        role.getDescription(),
                        configService.isRoleEnabled(role.getCode()),
                        userRepository.countActiveWithRole(role.getCode()),
                        role.getPermissions().stream().map(Permission::getCode).sorted().toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> permissions() {
        return permissionRepository.findAllByOrderByModuleCodeAscCodeAsc().stream()
                .map(permission -> new PermissionResponse(permission.getCode(), permission.getName(),
                        permission.getModuleCode()))
                .toList();
    }

    @Transactional
    public RoleResponse updatePermissions(RoleCode roleCode, Set<String> requestedCodes) {
        Role role = roleRepository.findAllWithPermissions().stream()
                .filter(candidate -> candidate.getCode() == roleCode)
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Role", roleCode));

        List<Permission> permissions = permissionRepository.findAllByCodeIn(requestedCodes);
        if (permissions.size() != requestedCodes.size()) {
            Set<String> known = permissions.stream().map(Permission::getCode).collect(Collectors.toSet());
            Set<String> unknown = new TreeSet<>(requestedCodes);
            unknown.removeAll(known);
            throw new BusinessRuleException("Unknown permissions: " + unknown);
        }

        if (roleCode == RoleCode.STUDENTS) {
            boolean staffPermission = permissions.stream()
                    .anyMatch(permission -> !STUDENT_PORTAL_MODULE.equals(permission.getModuleCode())
                            && !PermissionCode.MY_SCHEDULE_VIEW.name().equals(permission.getCode()));
            if (staffPermission) {
                throw new BusinessRuleException("Students can only hold their own read-only portal permissions");
            }
        }

        boolean takesAttendance = permissions.stream().anyMatch(permission -> ATTENDANCE_TAKING.contains(permission.getCode()));
        if (takesAttendance && !RoleCode.ATTENDANCE_TAKERS.contains(roleCode)) {
            throw new BusinessRuleException("Attendance is taken by mentors and faculty only. Remove "
                    + String.join(" and ", ATTENDANCE_TAKING) + " from " + role.getName() + "; it can still view attendance.");
        }

        Set<String> before = role.getPermissions().stream().map(Permission::getCode).collect(Collectors.toCollection(TreeSet::new));
        role.setPermissions(new HashSet<>(permissions));
        roleRepository.flush();

        guardStillGranted(PermissionCode.USER_MANAGE);
        guardStillGranted(PermissionCode.ROLE_MANAGE);

        Set<String> added = new TreeSet<>(requestedCodes);
        added.removeAll(before);
        Set<String> removed = new TreeSet<>(before);
        removed.removeAll(requestedCodes);
        auditService.record("Role", role.getId(), AuditService.UPDATE,
                "Changed permissions of " + roleCode + ": +" + added + " -" + removed);

        return new RoleResponse(role.getCode(), role.getName(), role.getDescription(),
                configService.isRoleEnabled(role.getCode()), userRepository.countActiveWithRole(role.getCode()),
                role.getPermissions().stream().map(Permission::getCode).sorted().toList());
    }

    /** At least one enabled role with active users must still hold the permission. */
    private void guardStillGranted(PermissionCode code) {
        boolean granted = roleRepository.findAllWithPermissions().stream()
                .filter(role -> configService.isRoleEnabled(role.getCode()))
                .filter(role -> role.getPermissions().stream().anyMatch(p -> p.getCode().equals(code.name())))
                .anyMatch(role -> userRepository.countActiveWithRole(role.getCode()) > 0);
        if (!granted) {
            throw new BusinessRuleException("This change would leave no active user with " + code.name());
        }
    }
}
