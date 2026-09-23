package com.coyotai.education.security;

import com.coyotai.education.platform.RoleCode;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The authenticated principal. Authorities are the permissions of the user's <em>enabled</em>
 * roles plus ROLE_ markers; a role switched off in configuration contributes nothing.
 */
public class AppUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String fullName;
    private final boolean active;
    private final boolean mustChangePassword;
    private final Set<RoleCode> assignedRoles;
    private final Set<RoleCode> enabledRoles;
    private final Set<String> permissions;
    private final Long studentId;
    private final Long mentorId;
    private final Long facultyId;
    private final List<GrantedAuthority> authorities;

    public AppUserDetails(Long id, String username, String password, String fullName, boolean active,
                          boolean mustChangePassword, Set<RoleCode> assignedRoles, Set<RoleCode> enabledRoles,
                          Set<String> permissions, Long studentId, Long mentorId, Long facultyId) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.active = active;
        this.mustChangePassword = mustChangePassword;
        this.assignedRoles = Set.copyOf(assignedRoles);
        this.enabledRoles = Set.copyOf(enabledRoles);
        this.permissions = Set.copyOf(permissions);
        this.studentId = studentId;
        this.mentorId = mentorId;
        this.facultyId = facultyId;

        List<GrantedAuthority> granted = new ArrayList<>();
        enabledRoles.forEach(role -> granted.add(new SimpleGrantedAuthority("ROLE_" + role.name())));
        permissions.forEach(permission -> granted.add(new SimpleGrantedAuthority(permission)));
        this.authorities = List.copyOf(granted);
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public Set<RoleCode> getAssignedRoles() {
        return assignedRoles;
    }

    public Set<RoleCode> getEnabledRoles() {
        return enabledRoles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean hasRole(RoleCode role) {
        return enabledRoles.contains(role);
    }

    public Long getStudentId() {
        return studentId;
    }

    public Long getMentorId() {
        return mentorId;
    }

    public Long getFacultyId() {
        return facultyId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
