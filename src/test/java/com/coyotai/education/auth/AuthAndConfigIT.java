package com.coyotai.education.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.coyotai.education.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign-in per role, the public branding endpoint and unauthenticated access. */
class AuthAndConfigIT extends IntegrationTestBase {

    @Test
    @DisplayName("Public configuration brands the app for the configured client and hides secrets")
    void publicConfiguration() throws Exception {
        String body = mockMvc.perform(get("/api/config/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.client.name").value("PROFINZ"))
                .andExpect(jsonPath("$.data.client.logo").value("/assets/branding/profinz-logo.png"))
                .andExpect(jsonPath("$.data.modules.academics").value(true))
                .andExpect(jsonPath("$.data.roles.directors").value(false))
                .andExpect(jsonPath("$.data.roles.sales").value(false))
                .andExpect(jsonPath("$.data.roles.accounts").value(false))
                .andExpect(jsonPath("$.data.roles.faculty").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(body.toLowerCase()).doesNotContain("secret", "password", "jdbc", "token");
    }

    @Test
    @DisplayName("Each enabled role signs in to its own portal")
    void portalsPerRole() throws Exception {
        assertThat(session("admin", "admin123").path("portal").asText()).isEqualTo("STAFF");
        assertThat(session("academic", "academic123").path("portal").asText()).isEqualTo("STAFF");
        assertThat(session("mentor", "mentor123").path("portal").asText()).isEqualTo("MENTOR");
        assertThat(session("faculty", "faculty123").path("portal").asText()).isEqualTo("FACULTY");

        JsonNode student = session("ADM26001", STUDENT_PASSWORD);
        assertThat(student.path("portal").asText()).isEqualTo("STUDENT");
        assertThat(student.path("studentId").isNumber()).isTrue();
        assertThat(values(student.path("permissions"))).isNotEmpty()
                .allSatisfy(permission -> assertThat(permission).startsWith("MY_"));
    }

    @Test
    @DisplayName("Wrong passwords are refused without saying which part was wrong")
    void wrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", "admin", "password", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", "nobody", "password", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @DisplayName("Protected APIs require a valid token")
    void unauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/students")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/students/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        getAs("Bearer not-a-real-token", "/api/students").andExpect(status().isUnauthorized());

        String token = adminToken();
        String tampered = token.substring(0, token.length() - 4) + "abcd";
        getAs(tampered, "/api/students").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A refresh token issues a new access token but is not itself an access token")
    void refresh() throws Exception {
        JsonNode auth = data(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("username", "academic", "password", "academic123"))));
        String refreshToken = auth.path("refreshToken").asText();

        getAs("Bearer " + refreshToken, "/api/auth/me").andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value("academic"));
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", auth.path("accessToken").asText())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Password change needs the current password and the new one works")
    void changePassword() throws Exception {
        String token = login("faculty2", "faculty123");
        postAs(token, "/api/auth/change-password", json("currentPassword", "wrong", "newPassword", "Faculty@2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("current password is incorrect")));
        postAs(token, "/api/auth/change-password", json("currentPassword", "faculty123", "newPassword", "Faculty@2026"))
                .andExpect(status().isOk());
        String newToken = login("faculty2", "Faculty@2026");
        // Put the demo password back for the other tests.
        postAs(newToken, "/api/auth/change-password", json("currentPassword", "Faculty@2026", "newPassword", "faculty123"))
                .andExpect(status().isOk());
    }

    private JsonNode session(String username, String password) throws Exception {
        return data(getAs(login(username, password), "/api/auth/me").andExpect(status().isOk()));
    }
}
