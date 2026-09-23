package com.coyotai.education.attendance;

import com.fasterxml.jackson.databind.JsonNode;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Attendance is taken by mentors (their batches) and faculty (their classes) only - enforced by
 * the API, not just the screen. Fixture: John (mentor) has CA Inter A; Ravi (faculty) teaches
 * Accounts/Taxation there and Accounts in CA Inter B; Anjali teaches Law/Costing in CA Inter A.
 * Each test uses its own day (12 to 14 days ago); saving a sheet replaces the demo marks of that day.
 */
class AttendanceTakingIT extends IntegrationTestBase {

    @Autowired
    private ProjectConfigService config;

    private String daysAgo(int days) {
        return config.today().minusDays(days).toString();
    }

    @Test
    @DisplayName("Administrators and academic staff can view attendance but never take it")
    void onlyMentorsAndFacultyTake() throws Exception {
        String admin = adminToken();
        long batch = batchId("CA Inter A");
        String date = daysAgo(12);

        JsonNode sheet = data(getAs(admin, "/api/attendance/sheet?batchId={b}&date={d}", batch, date)
                .andExpect(status().isOk()));
        assertThat(sheet.path("canMark").asBoolean()).isFalse();
        String body = json("batchId", batch, "date", date, "entries", entries(sheet, Map.of()));

        postAs(admin, "/api/attendance/bulk", body).andExpect(status().isForbidden());
        postAs(login("academic", "academic123"), "/api/attendance/bulk", body).andExpect(status().isForbidden());
        getAs(admin, "/api/attendance/classes").andExpect(status().isForbidden());
        getAs(admin, "/api/attendance?batchId={b}", batch).andExpect(status().isOk());
    }

    @Test
    @DisplayName("The roles screen cannot give attendance taking to any other role")
    void permissionsCannotReopenIt() throws Exception {
        String admin = adminToken();
        JsonNode roles = data(getAs(admin, "/api/rbac/roles"));
        assertThat(values(find(roles, "code", "ADMINISTRATIVE").path("permissions")))
                .contains("ATTENDANCE_VIEW").doesNotContain("ATTENDANCE_CREATE", "ATTENDANCE_UPDATE");
        assertThat(values(find(roles, "code", "FACULTY").path("permissions"))).contains("ATTENDANCE_CREATE");

        List<String> academics = new ArrayList<>(values(find(roles, "code", "ACADEMICS").path("permissions")));
        academics.add("ATTENDANCE_CREATE");
        putAs(admin, "/api/rbac/roles/ACADEMICS/permissions", json("permissions", academics))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("mentors and faculty only")));
    }

    @Test
    @DisplayName("A mentor takes attendance for the batches they mentor, and only those")
    void mentorOwnBatches() throws Exception {
        String mentor = login("mentor", "mentor123");
        String date = daysAgo(12);
        long batchA = batchId("CA Inter A");
        long batchB = batchId("CA Inter B");

        JsonNode classes = data(getAs(mentor, "/api/attendance/classes?date={d}", date).andExpect(status().isOk()));
        assertThat(values(classes, "batch", "name")).isNotEmpty().containsOnly("CA Inter A");
        assertThat(classes.get(0).path("scheduleId").isMissingNode() || classes.get(0).path("scheduleId").isNull())
                .as("the whole-day register comes first").isTrue();

        JsonNode sheetA = data(getAs(mentor, "/api/attendance/sheet?batchId={b}&date={d}", batchA, date));
        assertThat(sheetA.path("canMark").asBoolean()).isTrue();
        postAs(mentor, "/api/attendance/bulk", json("batchId", batchA, "date", date, "entries", entries(sheetA, Map.of())))
                .andExpect(status().isOk());

        JsonNode sheetB = data(getAs(adminToken(), "/api/attendance/sheet?batchId={b}&date={d}", batchB, date));
        postAs(mentor, "/api/attendance/bulk", json("batchId", batchB, "date", date, "entries", entries(sheetB, Map.of())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("batches you mentor")));
    }

    @Test
    @DisplayName("Faculty take attendance for the classes they teach, not whole days or other teachers' classes")
    void facultyOwnClasses() throws Exception {
        String faculty = login("faculty", "faculty123");
        String admin = adminToken();
        String date = daysAgo(12);
        long batchA = batchId("CA Inter A");

        JsonNode classes = data(getAs(faculty, "/api/attendance/classes?date={d}", date).andExpect(status().isOk()));
        assertThat(classes).isNotEmpty();
        assertThat(values(classes, "faculty", "name")).containsOnly("Ravi Menon");
        JsonNode ownClass = null;
        for (JsonNode entry : classes) {
            assertThat(entry.path("scheduleId").isNumber()).as("faculty get no whole-day registers").isTrue();
            if (entry.path("batch").path("id").asLong() == batchA) {
                ownClass = entry;
            }
        }
        assertThat(ownClass).isNotNull();
        long ownId = ownClass.path("scheduleId").asLong();

        JsonNode sheet = data(getAs(faculty, "/api/attendance/sheet?batchId={b}&date={d}&scheduleId={s}", batchA, date, ownId));
        assertThat(sheet.path("canMark").asBoolean()).isTrue();
        List<Map<String, Object>> everyone = entries(sheet, Map.of());
        postAs(faculty, "/api/attendance/bulk", json("batchId", batchA, "date", date, "scheduleId", ownId, "entries", everyone))
                .andExpect(status().isOk());

        postAs(faculty, "/api/attendance/bulk", json("batchId", batchA, "date", date, "entries", everyone))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("choose your class")));

        JsonNode schedule = data(getAs(admin, "/api/schedules?from={d}&to={d}&batchId={b}", date, date, batchA));
        long anjaliClass = find(schedule, "startTime", "20:15").path("id").asLong();
        String body = json("batchId", batchA, "date", date, "scheduleId", anjaliClass, "entries", everyone);
        postAs(faculty, "/api/attendance/bulk", body).andExpect(status().isForbidden());
        // The batch's mentor may take any of its classes.
        postAs(login("mentor", "mentor123"), "/api/attendance/bulk", body).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Late minutes, absence reasons and observations are kept; observations become one discipline record each")
    void detailsAndObservations() throws Exception {
        String mentor = login("mentor", "mentor123");
        String admin = adminToken();
        String date = daysAgo(13);
        long batch = batchId("CA Inter A");
        long ahmed = studentId("ADM26001");

        JsonNode sheet = data(getAs(mentor, "/api/attendance/sheet?batchId={b}&date={d}", batch, date));
        Map<String, Map<String, Object>> details = new LinkedHashMap<>();
        details.put("ADM26001", Map.of("status", "LATE", "lateMinutes", 30, "noUniform", true, "remarks", "Bus was late"));
        details.put("ADM26002", Map.of("status", "ABSENT", "absenceReason", "MEDICAL", "noUniform", true));
        details.put("ADM26003", Map.of("status", "PRESENT", "lateMinutes", 20, "noIdTag", true));
        String body = json("batchId", batch, "date", date, "entries", entries(sheet, details));

        postAs(mentor, "/api/attendance/bulk", body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.observationsRecorded").value(2));

        JsonNode saved = data(getAs(mentor, "/api/attendance/sheet?batchId={b}&date={d}", batch, date)).path("rows");
        JsonNode late = find(saved, "admissionNumber", "ADM26001");
        assertThat(late.path("status").asText()).isEqualTo("LATE");
        assertThat(late.path("lateMinutes").asInt()).isEqualTo(30);
        assertThat(late.path("noUniform").asBoolean()).isTrue();
        assertThat(late.path("remarks").asText()).isEqualTo("Bus was late");
        JsonNode absent = find(saved, "admissionNumber", "ADM26002");
        assertThat(absent.path("absenceReason").asText()).isEqualTo("MEDICAL");
        assertThat(absent.path("noUniform").asBoolean()).as("nothing is observed about an absent student").isFalse();
        JsonNode present = find(saved, "admissionNumber", "ADM26003");
        assertThat(present.path("lateMinutes").isMissingNode()).as("minutes only for late").isTrue();
        assertThat(present.path("noIdTag").asBoolean()).isTrue();

        String records = "/api/discipline-records?studentId={s}&from={d}&to={d}";
        assertThat(values(data(getAs(admin, records, ahmed, date, date)), "disciplineType", "name")).containsExactly("Uniform");

        // Saving again does not duplicate the record ...
        postAs(mentor, "/api/attendance/bulk", body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.observationsRecorded").value(0));
        assertThat(data(getAs(admin, records, ahmed, date, date))).hasSize(1);

        // ... and withdrawing the observation removes it.
        details.put("ADM26001", Map.of("status", "LATE", "lateMinutes", 30));
        postAs(mentor, "/api/attendance/bulk", json("batchId", batch, "date", date, "entries", entries(sheet, details)))
                .andExpect(status().isOk());
        assertThat(data(getAs(admin, records, ahmed, date, date))).isEmpty();
    }

    @Test
    @DisplayName("Corrections follow the same rule as taking attendance")
    void correctionsFollowTheRule() throws Exception {
        String mentor = login("mentor", "mentor123");
        String admin = adminToken();
        String date = daysAgo(14);
        long batch = batchId("CA Inter A");

        JsonNode sheet = data(getAs(mentor, "/api/attendance/sheet?batchId={b}&date={d}", batch, date));
        postAs(mentor, "/api/attendance/bulk", json("batchId", batch, "date", date, "entries", entries(sheet, Map.of())))
                .andExpect(status().isOk());
        String history = "/api/attendance?batchId={b}&from={d}&to={d}";
        JsonNode adminView = data(getAs(admin, history, batch, date, date)).path("content").get(0);
        long markId = adminView.path("id").asLong();
        assertThat(adminView.path("canCorrect").asBoolean()).isFalse();
        assertThat(data(getAs(mentor, history, batch, date, date)).path("content").get(0).path("canCorrect").asBoolean())
                .isTrue();

        String correction = json("status", "EXCUSED", "absenceReason", "PERSONAL");
        putAs(admin, "/api/attendance/{id}", correction, markId).andExpect(status().isForbidden());
        putAs(login("faculty", "faculty123"), "/api/attendance/{id}", correction, markId).andExpect(status().isForbidden());
        putAs(mentor, "/api/attendance/{id}", correction, markId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXCUSED"))
                .andExpect(jsonPath("$.data.absenceReason").value("PERSONAL"));
    }

    /** Everyone present, with per-student changes keyed by admission number. */
    private List<Map<String, Object>> entries(JsonNode sheet, Map<String, Map<String, Object>> changes) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (JsonNode row : sheet.path("rows")) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("studentId", row.path("studentId").asLong());
            entry.put("status", "PRESENT");
            entry.putAll(changes.getOrDefault(row.path("admissionNumber").asText(), Map.of()));
            entries.add(entry);
        }
        return entries;
    }
}
