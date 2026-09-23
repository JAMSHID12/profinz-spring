package com.coyotai.education.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.coyotai.education.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mentors and faculty hold the same permissions for every batch, but only reach the batches
 * (and, for faculty, subjects) they are assigned to. Fixture: John mentors CA Inter A; Sara
 * mentors CA Inter B and ACCA Skills A; Ravi teaches Accounts/Taxation in CA Inter A and
 * Accounts in CA Inter B; Anjali teaches Law/Costing in CA Inter A and Reporting in ACCA.
 */
class DataScopeIT extends IntegrationTestBase {

    @Test
    @DisplayName("A mentor sees only the students of the batches they mentor")
    void mentorStudents() throws Exception {
        JsonNode students = data(getAs(login("mentor", "mentor123"), "/api/students").andExpect(status().isOk()));
        assertThat(values(students, "batch", "name")).isNotEmpty().containsOnly("CA Inter A");
        assertThat(values(students, "admissionNumber")).contains("ADM26001").doesNotContain("ADM26006");

        JsonNode sara = data(getAs(login("mentor2", "mentor123"), "/api/students"));
        assertThat(values(sara, "batch", "name")).containsOnly("CA Inter B", "ACCA Skills A");
    }

    @Test
    @DisplayName("A mentor cannot open a student or batch of someone else's batch")
    void mentorCannotReachOtherBatches() throws Exception {
        String mentor = login("mentor", "mentor123");
        getAs(mentor, "/api/students/{id}", studentId("ADM26001")).andExpect(status().isOk());
        getAs(mentor, "/api/students/{id}", studentId("ADM26006")).andExpect(status().isForbidden());
        getAs(mentor, "/api/students/{id}/batch-history", studentId("ADM26006")).andExpect(status().isForbidden());

        assertThat(values(data(getAs(mentor, "/api/batches")), "name")).containsOnly("CA Inter A");
        getAs(mentor, "/api/batches/{id}", batchId("CA Inter B")).andExpect(status().isForbidden());

        String today = data(getAs(mentor, "/api/dashboard/mentor")).path("date").asText();
        getAs(mentor, "/api/attendance/sheet?batchId={b}&date={d}", batchId("CA Inter A"), today)
                .andExpect(status().isOk());
        getAs(mentor, "/api/attendance/sheet?batchId={b}&date={d}", batchId("CA Inter B"), today)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Faculty see the students of the batches they teach")
    void facultyStudents() throws Exception {
        JsonNode ravi = data(getAs(login("faculty", "faculty123"), "/api/students").andExpect(status().isOk()));
        assertThat(values(ravi, "batch", "name")).containsOnly("CA Inter A", "CA Inter B");

        JsonNode anjali = data(getAs(login("faculty2", "faculty123"), "/api/students"));
        assertThat(values(anjali, "batch", "name")).containsOnly("CA Inter A", "ACCA Skills A");
    }

    @Test
    @DisplayName("Faculty enter marks only for subjects they teach in that batch")
    void facultyMarksBySubject() throws Exception {
        JsonNode draft = find(data(getAs(login("academic", "academic123"), "/api/tests?status=DRAFT")),
                "title", "Taxation daily test 5");
        assertThat(draft.path("batch").path("name").asText()).isEqualTo("CA Inter A");
        long testId = draft.path("id").asLong();

        String ravi = login("faculty", "faculty123");     // teaches Taxation in CA Inter A
        String anjali = login("faculty2", "faculty123");  // teaches Law and Costing there
        getAs(ravi, "/api/tests/{id}/results", testId).andExpect(status().isOk());
        getAs(anjali, "/api/tests/{id}/results", testId).andExpect(status().isForbidden());
        putAs(anjali, "/api/tests/{id}/results", json("entries",
                List.of(Map.of("studentId", studentId("ADM26002"), "marksObtained", 20, "absent", false))), testId)
                .andExpect(status().isForbidden());

        // Faculty enter marks but do not publish.
        postAs(ravi, "/api/tests/{id}/status", json("status", "PUBLISHED"), testId).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Role dashboards are limited to their role and scope")
    void dashboards() throws Exception {
        String mentor = login("mentor", "mentor123");
        JsonNode dashboard = data(getAs(mentor, "/api/dashboard/mentor").andExpect(status().isOk()));
        assertThat(values(dashboard.path("batches"), "batch", "name")).containsOnly("CA Inter A");

        String faculty = login("faculty", "faculty123");
        getAs(faculty, "/api/dashboard/faculty").andExpect(status().isOk());
        getAs(faculty, "/api/dashboard/mentor").andExpect(status().isForbidden());
        getAs(mentor, "/api/dashboard/faculty").andExpect(status().isForbidden());

        // The general summary is open to every staff dashboard, but counted within scope only.
        JsonNode summary = data(getAs(mentor, "/api/dashboard/summary").andExpect(status().isOk()));
        assertThat(summary.path("activeStudents").asInt()).isEqualTo(data(getAs(mentor, "/api/students")).size());
        assertThat(summary.path("activeBatches").asInt()).isEqualTo(1);
        assertThat(summary.hasNonNull("feesOutstanding")).isFalse();
    }

    @Test
    @DisplayName("Global roles see every batch")
    void globalRoles() throws Exception {
        JsonNode students = data(getAs(login("academic", "academic123"), "/api/students"));
        assertThat(values(students, "batch", "name")).contains("CA Inter A", "CA Inter B", "ACCA Skills A");
    }
}
