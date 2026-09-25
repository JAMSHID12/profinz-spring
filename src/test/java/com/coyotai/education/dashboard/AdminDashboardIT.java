package com.coyotai.education.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.platform.bootstrap.DemoDataSeeder;
import com.coyotai.education.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The administrator's attendance and discipline dashboard. Fixture: John mentors CA Inter A; the
 * demo batches with a mentor have the last 60 days of attendance, and the eight extra batches also
 * have absence reasons and uniform / ID-tag notes on that history.
 */
class AdminDashboardIT extends IntegrationTestBase {

    private static final List<String> EXTRA_BATCHES = List.of("CA Inter C", "CMA India A", "CS Executive A", "ACCA Skills B",
            "CA Inter D", "CMA USA B", "CMA India B", "CS Executive B");
    private static final List<String> RISK_ORDER = List.of("CRITICAL", "AT_RISK", "MONITOR", "OK");

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private ProjectConfigService config;

    @Test
    @DisplayName("Only administrators get the dashboard and a student's attendance behind it")
    void administratorsOnly() throws Exception {
        long ahmed = studentId("ADM26001");
        for (String[] login : new String[][] {{"mentor", "mentor123"}, {"faculty", "faculty123"}, {"academic", "academic123"}}) {
            String token = login(login[0], login[1]);
            getAs(token, "/api/dashboard/admin").andExpect(status().isForbidden());
            // Even for a student of John's own batch.
            getAs(token, "/api/dashboard/attendance/students/{id}", ahmed).andExpect(status().isForbidden());
        }
        getAs(adminToken(), "/api/dashboard/admin").andExpect(status().isOk());
        getAs(adminToken(), "/api/dashboard/attendance/students/{id}", ahmed).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Totals, trend, classes, students, weekdays, reasons and weekly discipline all count the same marks")
    void totalsAgree() throws Exception {
        LocalDate today = config.today();
        JsonNode dashboard = data(getAs(adminToken(), "/api/dashboard/admin"));
        assertThat(dashboard.path("from").asText()).isEqualTo(today.minusDays(29).toString());
        assertThat(dashboard.path("to").asText()).isEqualTo(today.toString());

        JsonNode totals = dashboard.path("totals");
        long total = totals.path("totalClasses").asLong();
        assertThat(total).isPositive();
        assertThat(dashboard.path("granularity").asText()).isEqualTo("DAY");
        assertThat(dashboard.path("trend")).hasSize(30);
        assertThat(marks(dashboard.path("trend"))).isEqualTo(total);
        assertThat(summaries(dashboard.path("classes"))).isEqualTo(total);
        assertThat(summaries(dashboard.path("students"))).isEqualTo(total);
        long weekdays = 0;
        for (JsonNode day : dashboard.path("weekdays")) {
            weekdays += day.path("marks").asLong();
        }
        assertThat(weekdays).isEqualTo(total);
        long reasons = 0;
        for (JsonNode reason : dashboard.path("absenceReasons")) {
            reasons += reason.path("count").asLong();
        }
        assertThat(reasons).isEqualTo(totals.path("absent").asLong() + totals.path("excused").asLong());

        // Late arrivals are the late marks plus any late-coming records; the weeks add up to the totals.
        JsonNode discipline = dashboard.path("discipline");
        assertThat(discipline.path("late").asLong()).isGreaterThanOrEqualTo(totals.path("late").asLong());
        assertThat(dashboard.path("disciplineWeeks")).hasSize(5);
        for (String kind : List.of("late", "noUniform", "noIdTag", "other")) {
            long weekly = 0;
            for (JsonNode week : dashboard.path("disciplineWeeks")) {
                weekly += week.path(kind).asLong();
            }
            assertThat(weekly).as(kind).isEqualTo(discipline.path(kind).asLong());
        }

        assertThat(values(dashboard.path("classes"), "batch", "name")).contains("CA Inter A").containsAll(EXTRA_BATCHES);
        List<Double> classRates = new ArrayList<>();
        dashboard.path("classes").forEach(row -> classRates.add(row.path("summary").path("attendancePercentage").asDouble()));
        assertThat(classRates).as("lowest attendance first").isSorted();

        List<Integer> risks = new ArrayList<>();
        long below75 = 0;
        for (JsonNode student : dashboard.path("students")) {
            risks.add(RISK_ORDER.indexOf(student.path("risk").asText()));
            double attendance = student.path("summary").path("attendancePercentage").asDouble();
            below75 += attendance < 75 ? 1 : 0;
            if (attendance < 65) {
                assertThat(student.path("risk").asText()).as(student.path("fullName").asText()).isEqualTo("CRITICAL");
            }
            assertThat(student.path("issues").asLong()).isEqualTo(student.path("noUniform").asLong()
                    + student.path("noIdTag").asLong() + student.path("otherIssues").asLong());
        }
        assertThat(risks).as("most serious first").doesNotContain(-1).isSorted();
        assertThat(dashboard.path("studentsAtRisk").asLong()).isEqualTo(below75);

        JsonNode now = dashboard.path("today");
        assertThat(now.path("present").asLong() + now.path("absent").asLong()).isEqualTo(now.path("marked").asLong());
        assertThat(now.path("late").asLong()).isLessThanOrEqualTo(now.path("present").asLong());
        assertThat(now.path("absentWithoutReason").asLong()).isLessThanOrEqualTo(now.path("absent").asLong());

        // The 30 days before are demo history too; their trend comes only when asked for.
        assertThat(dashboard.path("previousTotals").path("totalClasses").asLong()).isPositive();
        assertThat(dashboard.path("previousTrend")).isEmpty();
        JsonNode compared = data(getAs(adminToken(), "/api/dashboard/admin?compare=true"));
        assertThat(compared.path("previousTrend")).hasSize(30);
        assertThat(compared.path("previousTrend").get(29).path("start").asText()).isEqualTo(today.minusDays(30).toString());
        assertThat(marks(compared.path("previousTrend"))).isEqualTo(compared.path("previousTotals").path("totalClasses").asLong());
    }

    @Test
    @DisplayName("Filters narrow everything to a course, a class or a mentor's classes; long periods go by week")
    void filters() throws Exception {
        String admin = adminToken();
        JsonNode caInterA = find(data(getAs(admin, "/api/batches")), "name", "CA Inter A");
        long courseId = caInterA.path("course").path("id").asLong();

        JsonNode byCourse = data(getAs(admin, "/api/dashboard/admin?courseId={c}", courseId));
        assertThat(byCourse.path("classes")).isNotEmpty();
        assertThat(values(byCourse.path("classes"), "course", "id")).containsOnly(String.valueOf(courseId));

        JsonNode byBatch = data(getAs(admin, "/api/dashboard/admin?batchId={b}", caInterA.path("id").asLong()));
        assertThat(values(byBatch.path("classes"), "batch", "name")).containsOnly("CA Inter A");
        assertThat(values(byBatch.path("students"), "batch", "name")).containsOnly("CA Inter A");
        assertThat(byBatch.path("activeStudents").asLong()).isEqualTo(caInterA.path("studentCount").asLong());

        long john = find(data(getAs(admin, "/api/mentors")), "fullName", "John Mathew").path("id").asLong();
        JsonNode byMentor = data(getAs(admin, "/api/dashboard/admin?mentorId={m}", john));
        assertThat(values(byMentor.path("classes"), "batch", "name")).containsOnly("CA Inter A");
        assertThat(byMentor.path("totals").path("totalClasses").asLong())
                .isEqualTo(byBatch.path("totals").path("totalClasses").asLong());

        LocalDate today = config.today();
        JsonNode quarter = data(getAs(admin, "/api/dashboard/admin?from={f}&to={t}", today.minusDays(89), today));
        assertThat(quarter.path("granularity").asText()).isEqualTo("WEEK");
        assertThat(quarter.path("trend")).hasSize(13);
        assertThat(quarter.path("trend").get(0).path("label").asText()).isEqualTo("W1");
        assertThat(marks(quarter.path("trend"))).isEqualTo(quarter.path("totals").path("totalClasses").asLong());

        getAs(admin, "/api/dashboard/admin?from={f}&to={t}", "2000-01-01", today).andExpect(status().isBadRequest());
        getAs(admin, "/api/dashboard/admin?from={f}&to={t}", today, today.minusDays(3)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("One student's attendance: every mark, a day per date, and at most a year at a time")
    void oneStudent() throws Exception {
        String admin = adminToken();
        long ahmed = studentId("ADM26001");
        getAs(admin, "/api/dashboard/attendance/students/{id}?from={f}&to={t}", ahmed, "2000-01-01", "2099-12-31")
                .andExpect(status().isBadRequest());

        JsonNode detail = data(getAs(admin, "/api/dashboard/attendance/students/{id}", ahmed));
        assertThat(detail.path("fullName").asText()).isEqualTo("Ahmed Kumar");
        int records = detail.path("records").size();
        assertThat(records).isPositive().isEqualTo(detail.path("summary").path("totalClasses").asInt());
        int marks = 0;
        for (JsonNode day : detail.path("days")) {
            marks += day.path("marks").asInt();
        }
        assertThat(marks).isEqualTo(records);
    }

    @Test
    @DisplayName("The demo has twelve batches, 60 days of attendance with reasons and discipline notes, and a restart adds nothing again")
    void demoData() throws Exception {
        String admin = adminToken();
        JsonNode batches = data(getAs(admin, "/api/batches"));
        assertThat(values(batches, "name")).contains("CA Inter A", "CA Inter B", "CA Inter C", "CA Inter D",
                "CMA India A", "CMA India B", "CMA USA A", "CMA USA B", "ACCA Skills A", "ACCA Skills B",
                "CS Executive A", "CS Executive B");
        for (String name : EXTRA_BATCHES) {
            JsonNode batch = find(batches, "name", name);
            assertThat(batch.path("studentCount").asInt()).as(name).isGreaterThanOrEqualTo(5);
            assertThat(batch.path("mentor").path("name").asText()).as(name).isNotBlank();
        }
        assertThat(find(batches, "name", "CA Inter A").path("studentCount").asInt()).isEqualTo(10);
        assertThat(find(batches, "name", "CMA USA A").path("studentCount").asInt()).isEqualTo(3);

        LocalDate yesterday = config.today().minusDays(1);
        String period = "/api/dashboard/admin?from={f}&to={t}";
        LocalDate from = yesterday.minusDays(DemoDataSeeder.HISTORY_DAYS - 1);
        JsonNode history = data(getAs(admin, period, from, yesterday));
        assertThat(history.path("granularity").asText()).isEqualTo("WEEK");
        for (JsonNode week : history.path("trend")) {
            assertThat(week.path("present").asLong() + week.path("absent").asLong() + week.path("late").asLong()
                    + week.path("excused").asLong()).as(week.path("label").asText()).isPositive();
        }
        JsonNode reasons = history.path("absenceReasons");
        assertThat(reasons).hasSize(2);
        assertThat(find(reasons, "key", "INFORMED").path("count").asLong()).isPositive();
        assertThat(find(reasons, "key", "NOT_INFORMED").path("count").asLong()).isPositive();
        assertThat(history.path("discipline").path("noUniform").asLong()).isPositive();
        assertThat(history.path("discipline").path("noIdTag").asLong()).isPositive();

        int students = data(getAs(admin, "/api/students")).size();
        seeder.run(null);

        assertThat(data(getAs(admin, "/api/batches"))).hasSize(batches.size());
        assertThat(data(getAs(admin, "/api/students"))).hasSize(students);
        JsonNode again = data(getAs(admin, period, from, yesterday));
        assertThat(again.path("totals")).isEqualTo(history.path("totals"));
        assertThat(again.path("absenceReasons")).isEqualTo(reasons);
        assertThat(again.path("discipline")).isEqualTo(history.path("discipline"));
    }

    /** Every mark of a trend, whatever its status. */
    private static long marks(JsonNode points) {
        long total = 0;
        for (JsonNode point : points) {
            total += point.path("present").asLong() + point.path("absent").asLong() + point.path("late").asLong()
                    + point.path("excused").asLong();
        }
        return total;
    }

    private static long summaries(JsonNode rows) {
        long total = 0;
        for (JsonNode row : rows) {
            total += row.path("summary").path("totalClasses").asLong();
        }
        return total;
    }
}
