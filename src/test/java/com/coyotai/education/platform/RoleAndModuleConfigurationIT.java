package com.coyotai.education.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.coyotai.education.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The same build behaving differently per client configuration. The tests flip
 * {@code project.role.*} and {@code project.module.*} on the live properties bean - exactly
 * what a different application.yml would do - and restore them afterwards.
 */
class RoleAndModuleConfigurationIT extends IntegrationTestBase {

    @Autowired
    private ProjectProperties properties;

    private final Map<RoleCode, Boolean> savedRoles = new EnumMap<>(RoleCode.class);
    private final Map<ModuleCode, Boolean> savedModules = new EnumMap<>(ModuleCode.class);

    @BeforeEach
    void snapshot() {
        properties.getRole().forEach((role, toggle) -> savedRoles.put(role, toggle.isEnabled()));
        properties.getModule().forEach((module, toggle) -> savedModules.put(module, toggle.isEnabled()));
    }

    @AfterEach
    void restore() {
        properties.getRole().clear();
        savedRoles.forEach((role, enabled) -> properties.getRole().put(role, toggle(enabled)));
        properties.getModule().clear();
        savedModules.forEach((module, enabled) -> properties.getModule().put(module, toggle(enabled)));
    }

    @Test
    @DisplayName("A disabled role cannot sign in")
    void disabledRoleCannotSignIn() throws Exception {
        setRole(RoleCode.FACULTY, false);
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", "faculty", "password", "faculty123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Your role is not enabled for PROFINZ"));
    }

    @Test
    @DisplayName("Disabling a role also ends sessions that are already open")
    void disablingRoleEndsOpenSessions() throws Exception {
        String token = login("mentor", "mentor123");
        getAs(token, "/api/students").andExpect(status().isOk());

        setRole(RoleCode.MENTORS, false);
        getAs(token, "/api/students").andExpect(status().isUnauthorized());
        getAs(token, "/api/auth/me").andExpect(status().isUnauthorized());

        setRole(RoleCode.MENTORS, true);
        getAs(token, "/api/students").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Enabling a role for a client makes the same accounts usable - no code change")
    void enablingRoleWorksWithoutCodeChanges() throws Exception {
        postAs(adminToken(), "/api/users", json(
                "username", "director.one",
                "password", "Director@2026",
                "fullName", "Director One",
                "roles", List.of("DIRECTORS")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", "director.one", "password", "Director@2026")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("not enabled")));

        setRole(RoleCode.DIRECTORS, true);
        String token = login("director.one", "Director@2026");
        JsonNode session = data(getAs(token, "/api/auth/me"));
        assertThat(session.path("portal").asText()).isEqualTo("STAFF");
        assertThat(values(session.path("permissions"))).contains("STUDENT_VIEW")
                .allSatisfy(permission -> assertThat(permission).endsWith("_VIEW"));

        // Directors oversee; they do not change records.
        getAs(token, "/api/students").andExpect(status().isOk());
        postAs(token, "/api/students", json("admissionNumber", "DIR0001", "fullName", "Not Allowed"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A disabled module is refused by the API for everyone, even administrators")
    void disabledModuleIsRefused() throws Exception {
        String admin = adminToken();
        String student = login("ADM26001", STUDENT_PASSWORD);
        getAs(admin, "/api/fees/plans").andExpect(status().isOk());
        getAs(student, "/api/students/me/fees").andExpect(status().isOk());

        setModule(ModuleCode.FEES, false);
        getAs(admin, "/api/fees/plans")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("The Fees module is not enabled for this client"));
        getAs(admin, "/api/reports/fees").andExpect(status().isForbidden());
        getAs(student, "/api/students/me/fees").andExpect(status().isForbidden());
        // The rest of the portal keeps working and tells the UI to hide fees.
        getAs(student, "/api/students/me/dashboard")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feesEnabled").value(false));
    }

    @Test
    @DisplayName("Switching off the student portal closes every /me endpoint")
    void studentPortalModule() throws Exception {
        String student = login("ADM26001", STUDENT_PASSWORD);
        setModule(ModuleCode.STUDENT_PORTAL, false);
        getAs(student, "/api/students/me").andExpect(status().isForbidden());
        getAs(student, "/api/students/me/attendance").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Public configuration follows the switches so the UI can hide menus")
    void publicConfigFollowsSwitches() throws Exception {
        setRole(RoleCode.SALES, true);
        setModule(ModuleCode.REPORTS, false);
        mockMvc.perform(get("/api/config/public"))
                .andExpect(jsonPath("$.data.roles.sales").value(true))
                .andExpect(jsonPath("$.data.modules.reports").value(false));
    }

    @Test
    @DisplayName("Role permissions live in the database and changes apply on the next request")
    void permissionChangesApplyImmediately() throws Exception {
        String admin = adminToken();
        String mentor = login("mentor", "mentor123");
        getAs(mentor, "/api/attendance").andExpect(status().isOk());

        JsonNode mentors = find(data(getAs(admin, "/api/rbac/roles")), "code", "MENTORS");
        List<String> original = values(mentors.path("permissions"));
        List<String> reduced = original.stream().filter(code -> !code.equals("ATTENDANCE_VIEW")).toList();
        try {
            putAs(admin, "/api/rbac/roles/MENTORS/permissions", json("permissions", reduced))
                    .andExpect(status().isOk());
            getAs(mentor, "/api/attendance").andExpect(status().isForbidden());
        } finally {
            putAs(admin, "/api/rbac/roles/MENTORS/permissions", json("permissions", original))
                    .andExpect(status().isOk());
        }
        getAs(mentor, "/api/attendance").andExpect(status().isOk());
    }

    @Test
    @DisplayName("The students role cannot be given staff permissions")
    void studentRoleStaysSelfService() throws Exception {
        putAs(adminToken(), "/api/rbac/roles/STUDENTS/permissions",
                json("permissions", Set.of("MY_PROFILE_VIEW", "STUDENT_VIEW")))
                .andExpect(status().isBadRequest());
    }

    private void setRole(RoleCode role, boolean enabled) {
        properties.getRole().put(role, toggle(enabled));
    }

    private void setModule(ModuleCode module, boolean enabled) {
        properties.getModule().put(module, toggle(enabled));
    }

    private static ProjectProperties.Toggle toggle(boolean enabled) {
        ProjectProperties.Toggle toggle = new ProjectProperties.Toggle();
        toggle.setEnabled(enabled);
        return toggle;
    }
}
