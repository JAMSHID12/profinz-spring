package com.coyotai.education.security;

import com.coyotai.education.auth.Permission;
import com.coyotai.education.auth.Role;
import com.coyotai.education.auth.User;
import com.coyotai.education.auth.UserRepository;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.platform.RoleCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Builds the principal on every request, so deactivating a user, revoking a permission or
 * switching a role off in configuration takes effect immediately - even for issued tokens.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final ProjectConfigService configService;
    private final JdbcTemplate jdbcTemplate;

    public AppUserDetailsService(UserRepository userRepository, ProjectConfigService configService,
                                 JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.configService = configService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public AppUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findWithAuthoritiesByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Set<RoleCode> assigned = EnumSet.noneOf(RoleCode.class);
        Set<RoleCode> enabled = EnumSet.noneOf(RoleCode.class);
        Set<String> permissions = new HashSet<>();
        for (Role role : user.getRoles()) {
            assigned.add(role.getCode());
            if (configService.isRoleEnabled(role.getCode())) {
                enabled.add(role.getCode());
                for (Permission permission : role.getPermissions()) {
                    permissions.add(permission.getCode());
                }
            }
        }

        // user_id is unique across the profile tables, so one round trip finds all three.
        Map<String, Object> profiles = jdbcTemplate.queryForMap("""
                SELECT (SELECT id FROM students WHERE user_id = ?) AS student_id,
                       (SELECT id FROM mentors WHERE user_id = ?)  AS mentor_id,
                       (SELECT id FROM faculty WHERE user_id = ?)  AS faculty_id
                """, user.getId(), user.getId(), user.getId());

        return new AppUserDetails(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getFullName(),
                user.isActive(),
                user.isMustChangePassword(),
                assigned,
                enabled,
                permissions,
                toLong(profiles.get("student_id")),
                toLong(profiles.get("mentor_id")),
                toLong(profiles.get("faculty_id")));
    }

    private Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
