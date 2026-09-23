package com.coyotai.education.student.portal;

import com.fasterxml.jackson.databind.JsonNode;
import com.coyotai.education.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A student sees their own published records and nothing else. */
class StudentPortalIT extends IntegrationTestBase {

    @Test
    @DisplayName("The portal resolves the student from the login - no ids in the URL")
    void ownProfile() throws Exception {
        String token = login("ADM26001", STUDENT_PASSWORD);
        getAs(token, "/api/students/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.admissionNumber").value("ADM26001"))
                .andExpect(jsonPath("$.data.fullName").value("Ahmed Kumar"))
                .andExpect(jsonPath("$.data.batch.name").value("CA Inter A"))
                .andExpect(jsonPath("$.data.mentor.name").value("John Mathew"));

        getAs(token, "/api/students/me/dashboard")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Ahmed Kumar"))
                .andExpect(jsonPath("$.data.attendancePercentage").isNumber())
                .andExpect(jsonPath("$.data.feesEnabled").value(true));
    }

    @Test
    @DisplayName("Test and exam results appear only once published")
    void onlyPublishedResults() throws Exception {
        String token = login("ADM26001", STUDENT_PASSWORD);

        List<String> tests = values(data(getAs(token, "/api/students/me/tests").andExpect(status().isOk())), "title");
        assertThat(tests).contains("Accounts daily test 1", "Accounts weekly test")
                .doesNotContain("Taxation daily test 5");

        JsonNode exams = data(getAs(token, "/api/students/me/exams").andExpect(status().isOk()));
        assertThat(values(exams.path("results"), "examName"))
                .contains("Accounts class exam")
                .doesNotContain("Taxation model exam");
    }

    @Test
    @DisplayName("Attendance history and percentage come from the student's own records")
    void ownAttendance() throws Exception {
        JsonNode attendance = data(getAs(login("ADM26001", STUDENT_PASSWORD), "/api/students/me/attendance")
                .andExpect(status().isOk()));
        assertThat(attendance.path("summary").path("totalClasses").asLong()).isPositive();
        assertThat(attendance.path("history")).isNotEmpty();
    }

    @Test
    @DisplayName("Fines, discipline and progress cards are the student's own")
    void ownFinesAndProgress() throws Exception {
        String ahmed = login("ADM26001", STUDENT_PASSWORD);
        JsonNode fines = data(getAs(ahmed, "/api/students/me/fines"));
        assertThat(values(fines, "admissionNumber")).containsOnly("ADM26001");
        assertThat(values(fines, "status")).contains("PENDING");
        assertThat(data(getAs(ahmed, "/api/students/me/discipline"))).isNotEmpty();

        JsonNode cards = data(getAs(ahmed, "/api/students/me/progress-cards"));
        assertThat(cards).hasSize(1);
        long cardId = cards.get(0).path("id").asLong();
        getAs(ahmed, "/api/students/me/progress-cards/{id}", cardId).andExpect(status().isOk());

        // Another student cannot open it, and is not even told it exists.
        String divya = login("ADM26002", STUDENT_PASSWORD);
        getAs(divya, "/api/students/me/progress-cards/{id}", cardId).andExpect(status().isNotFound());
        assertThat(data(getAs(divya, "/api/students/me/fines"))).isEmpty();
    }

    @Test
    @DisplayName("Students cannot reach staff APIs, not even for their own record")
    void staffApisAreClosed() throws Exception {
        String token = login("ADM26001", STUDENT_PASSWORD);
        long ownId = data(getAs(token, "/api/students/me")).path("id").asLong();
        long batchA = batchId("CA Inter A");

        getAs(token, "/api/students").andExpect(status().isForbidden());
        getAs(token, "/api/students/{id}", ownId).andExpect(status().isForbidden());
        getAs(token, "/api/attendance?studentId={id}", ownId).andExpect(status().isForbidden());
        getAs(token, "/api/fees/student/{id}", ownId).andExpect(status().isForbidden());
        getAs(token, "/api/tests").andExpect(status().isForbidden());
        getAs(token, "/api/dashboard/summary").andExpect(status().isForbidden());
        getAs(token, "/api/users").andExpect(status().isForbidden());
        getAs(token, "/api/audit-logs").andExpect(status().isForbidden());
        postAs(token, "/api/attendance/bulk", json("batchId", batchA, "date", "2026-01-05",
                "entries", List.of(Map.of("studentId", ownId, "status", "PRESENT"))))
                .andExpect(status().isForbidden());
    }
}
