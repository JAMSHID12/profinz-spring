package com.coyotai.education.academic;

import com.fasterxml.jackson.databind.JsonNode;
import com.coyotai.education.common.RecordStatus;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.support.IntegrationTestBase;
import com.coyotai.education.tenant.ClientRegistry;
import com.coyotai.education.tenant.TenantContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end academic workflows. Each test creates its own records so tests stay independent. */
class AcademicWorkflowIT extends IntegrationTestBase {

    @Autowired
    private ProjectConfigService config;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ClientRegistry clientRegistry;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("Marks stay private through DRAFT and REVIEW and appear once PUBLISHED")
    void publicationWorkflow() throws Exception {
        String academic = login("academic", "academic123");
        String ravi = login("faculty", "faculty123");
        String student = login("ADM26001", STUDENT_PASSWORD);
        String title = "Workflow check " + System.nanoTime();

        long testId = data(postAs(academic, "/api/tests", json(
                "testType", "DAILY", "title", title, "batchId", batchId("CA Inter A"),
                "subjectId", subjectId("Taxation"), "testDate", config.today().toString(), "maxMarks", 25))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))).path("id").asLong();

        List<Map<String, Object>> marks = new ArrayList<>();
        for (JsonNode row : data(getAs(ravi, "/api/tests/{id}/results", testId)).path("rows")) {
            marks.add(entry(row.path("studentId").asLong(), 18));
        }
        assertThat(marks).isNotEmpty();

        // Marks outside 0..max are refused.
        putAs(ravi, "/api/tests/{id}/results", json("entries", List.of(entry(marks.get(0).get("studentId"), 26))), testId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("exceed the maximum")));
        putAs(ravi, "/api/tests/{id}/results", json("entries", marks), testId).andExpect(status().isOk());
        assertThat(studentTestTitles(student)).doesNotContain(title);

        postAs(academic, "/api/tests/{id}/status", json("status", "REVIEW"), testId).andExpect(status().isOk());
        assertThat(studentTestTitles(student)).doesNotContain(title);

        postAs(academic, "/api/tests/{id}/status", json("status", "PUBLISHED"), testId).andExpect(status().isOk());
        assertThat(studentTestTitles(student)).contains(title);

        // Published marks are locked, and publishing never silently reverts to draft.
        putAs(ravi, "/api/tests/{id}/results", json("entries", marks), testId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("locked")));
        postAs(academic, "/api/tests/{id}/status", json("status", "DRAFT"), testId).andExpect(status().isBadRequest());

        JsonNode audit = data(getAs(adminToken(), "/api/audit-logs?entityType=Test&entityId={id}", testId)).path("content");
        assertThat(values(audit, "action")).contains("PUBLISH");
    }

    @Test
    @DisplayName("Attendance is saved once per student and day; re-saving never re-notifies parents")
    void attendance() throws Exception {
        String mentor = login("mentor", "mentor123");
        long batch = batchId("CA Inter A");
        LocalDate today = config.today();
        long ahmed = studentId("ADM26001");

        List<Map<String, Object>> entries = new ArrayList<>();
        for (JsonNode student : data(getAs(mentor, "/api/students?batchId={b}", batch))) {
            long id = student.path("id").asLong();
            entries.add(Map.of("studentId", id, "status", id == ahmed ? "ABSENT" : "PRESENT"));
        }
        String sheet = json("batchId", batch, "date", today.toString(), "entries", entries);

        postAs(mentor, "/api/attendance/bulk", sheet)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(entries.size()))
                .andExpect(jsonPath("$.data.notificationsQueued").value(1));
        postAs(mentor, "/api/attendance/bulk", sheet)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(0))
                .andExpect(jsonPath("$.data.updated").value(entries.size()))
                .andExpect(jsonPath("$.data.notificationsQueued").value(0));

        postAs(mentor, "/api/attendance/bulk", json("batchId", batch, "date", today.plusDays(1).toString(),
                "entries", entries))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("future date")));

        JsonNode history = data(getAs(login("ADM26001", STUDENT_PASSWORD), "/api/students/me/attendance")).path("history");
        JsonNode todays = find(history, "date", today.toString());
        assertThat(todays.path("status").asText()).isEqualTo("ABSENT");
    }

    @Test
    @DisplayName("Double-booking a teacher, a batch or a room is refused")
    void scheduleConflicts() throws Exception {
        String academic = login("academic", "academic123");
        String day = config.today().plusDays(3).toString();
        long batchB = batchId("CA Inter B");
        long accounts = subjectId("Accounts");
        long ravi = facultyId("Ravi Menon");

        // Ravi teaches CA Inter A from 18:00 to 20:00 every day of the fixture.
        postAs(academic, "/api/schedules", schedule(batchB, accounts, ravi, day, "19:00", "20:30", "Room 9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Schedule conflict")));

        JsonNode conflicts = data(postAs(academic, "/api/schedules/conflicts",
                schedule(batchB, accounts, ravi, day, "17:00", "18:30", "Room 1")));
        assertThat(values(conflicts, "type")).contains("FACULTY", "BATCH", "ROOM");

        postAs(academic, "/api/schedules", schedule(batchB, accounts, ravi, day, "21:30", "22:30", "Room 9"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Moving a student keeps the batch history, and their new login works")
    void transferAndStudentLogin() throws Exception {
        String academic = login("academic", "academic123");
        JsonNode reference = find(data(getAs(academic, "/api/batches")), "name", "CA Inter A");
        long course = reference.path("course").path("id").asLong();
        long year = reference.path("academicYear").path("id").asLong();

        long morning = data(postAs(academic, "/api/batches", json("name", "Transfer Morning", "courseId", course,
                "academicYearId", year, "capacity", 30)).andExpect(status().isOk())).path("id").asLong();
        long evening = data(postAs(academic, "/api/batches", json("name", "Transfer Evening", "courseId", course,
                "academicYearId", year, "capacity", 30)).andExpect(status().isOk())).path("id").asLong();

        long student = data(postAs(academic, "/api/students", json("parentName", "Transfer Parent", "parentPhoneNumber", "+919876543210",
                "fullName", "Transfer Student", "batchId", morning)).andExpect(status().isOk())).path("id").asLong();

        postAs(academic, "/api/students/{id}/transfer", json("batchId", evening, "reason", "Moved to evening timing"), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batch.name").value("Transfer Evening"));

        JsonNode history = data(getAs(academic, "/api/students/{id}/batch-history", student));
        assertThat(history).hasSize(2);
        JsonNode closed = find(history, "status", "TRANSFERRED");
        assertThat(closed.path("batch").path("name").asText()).isEqualTo("Transfer Morning");
        assertThat(closed.path("endDate").isNull()).isFalse();
        assertThat(find(history, "status", "CURRENT").path("batch").path("name").asText()).isEqualTo("Transfer Evening");

        // Admin issues a portal login; the student signs in with the configured default password.
        postAs(adminToken(), "/api/students/{id}/login", "{}", student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(data(getAs(academic, "/api/students/{id}", student)).path("admissionNumber").asText()));
        String token = login(data(getAs(academic, "/api/students/{id}", student)).path("admissionNumber").asText(), config.student().getDefaultPassword());
        JsonNode session = data(getAs(token, "/api/auth/me"));
        assertThat(session.path("portal").asText()).isEqualTo("STUDENT");
        assertThat(session.path("mustChangePassword").asBoolean()).isTrue();
        getAs(token, "/api/students/me").andExpect(jsonPath("$.data.batch.name").value("Transfer Evening"));
    }

    @Test
    @DisplayName("Payments get sequential receipt numbers and cannot exceed what is due")
    void payments() throws Exception {
        String admin = adminToken();
        JsonNode installment = null;
        for (JsonNode candidate : data(getAs(admin, "/api/fees/installments"))) {
            if (candidate.path("pendingAmount").decimalValue().compareTo(new BigDecimal("2000")) > 0
                    && !candidate.path("status").asText().equals("WAIVED")) {
                installment = candidate;
                break;
            }
        }
        assertThat(installment).as("an installment with money due").isNotNull();
        long id = installment.path("id").asLong();
        BigDecimal pending = installment.path("pendingAmount").decimalValue();

        JsonNode payment = data(postAs(admin, "/api/payments", json("installmentId", id, "amount", 1000,
                "paymentMethod", "UPI", "referenceNumber", "UPI-TEST-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptNumber", matchesPattern("PROFINZ-RCPT-\\d{4}-\\d{6}"))));
        getAs(admin, "/api/payments/{id}/receipt", payment.path("id").asLong()).andExpect(status().isOk());

        BigDecimal tooMuch = pending.subtract(new BigDecimal("1000")).add(BigDecimal.ONE);
        postAs(admin, "/api/payments", json("installmentId", id, "amount", tooMuch, "paymentMethod", "CASH"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("exceeds the pending amount")));
    }

    @Test
    @DisplayName("An empty required field says it is required, not that its format is wrong")
    void requiredMessageWins() throws Exception {
        postAs(login("academic", "academic123"), "/api/students", json("admissionNumber", "", "fullName", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.parentName").value("Parent name is required"))
                .andExpect(jsonPath("$.errors.fullName").value("Student name is required"));
    }

    @Test
    @DisplayName("Records of another client are invisible, even by id")
    void tenantIsolation() throws Exception {
        long profinzCourse = find(data(getAs(adminToken(), "/api/courses")), "code", "CA_INTER").path("id").asLong();
        Long otherClient = clientRegistry.findOrCreate("OTHER_ACADEMY", "Other Academy");

        Map<String, Object> seenByOther = TenantContext.callAs(otherClient, () -> transactionTemplate.execute(tx -> {
            Course course = new Course();
            course.setCode("OTHER_ONLY");
            course.setName("Other academy course");
            course.setStatus(RecordStatus.ACTIVE);
            courseRepository.save(course);
            Map<String, Object> seen = new HashMap<>();
            seen.put("codes", courseRepository.findAll().stream().map(Course::getCode).toList());
            seen.put("profinzById", courseRepository.findById(profinzCourse).isPresent());
            return seen;
        }));

        assertThat(seenByOther.get("codes")).isEqualTo(List.of("OTHER_ONLY"));
        assertThat(seenByOther.get("profinzById")).isEqualTo(false);
        assertThat(values(data(getAs(adminToken(), "/api/courses")), "code"))
                .contains("CA_INTER").doesNotContain("OTHER_ONLY");
    }

    private List<String> studentTestTitles(String token) throws Exception {
        return values(data(getAs(token, "/api/students/me/tests")), "title");
    }

    private static Map<String, Object> entry(Object studentId, int marks) {
        return Map.of("studentId", studentId, "marksObtained", marks, "absent", false);
    }

    private String schedule(long batch, long subject, long faculty, String date, String start, String end, String room)
            throws Exception {
        return json("batchId", batch, "subjectId", subject, "facultyId", faculty, "scheduleDate", date,
                "startTime", start, "endTime", end, "room", room);
    }
}
