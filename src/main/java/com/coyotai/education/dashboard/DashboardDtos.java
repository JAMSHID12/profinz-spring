package com.coyotai.education.dashboard;

import com.coyotai.education.academic.AcademicDtos.BatchResponse;
import com.coyotai.education.assessment.AssessmentDtos.ExamResponse;
import com.coyotai.education.assessment.AssessmentDtos.TestResponse;
import com.coyotai.education.attendance.AttendanceDtos.Record;
import com.coyotai.education.attendance.AttendanceStatus;
import com.coyotai.education.attendance.AttendanceSummary;
import com.coyotai.education.common.Ref;
import com.coyotai.education.notification.NotificationDtos.LogEntry;
import com.coyotai.education.parentmeeting.ParentMeetingService.MeetingResponse;
import com.coyotai.education.progress.ProgressCardService.Card;
import com.coyotai.education.schedule.ScheduleDtos.EntryExitResponse;
import com.coyotai.education.schedule.ScheduleDtos.ScheduleResponse;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record AttendanceToday(long present, long absent, long late, long excused, long marked,
                                  long batchesMarked, long activeBatches) {
    }

    /** Centre-wide (or scope-limited) overview for administrative and academic staff. */
    public record Summary(
            LocalDate date,
            long activeStudents,
            long activeBatches,
            long activeCourses,
            long mentors,
            long faculty,
            AttendanceToday attendance,
            List<ScheduleResponse> todaysClasses,
            List<ExamResponse> upcomingExams,
            long resultsAwaitingReview,
            long progressCardsAwaitingReview,
            BigDecimal pendingFines,
            BigDecimal feesOutstanding,
            BigDecimal feesOverdue,
            Long queuedMessages,
            Long failedMessages,
            List<LogEntry> recentMessages
    ) {
    }

    public record BatchSnapshot(BatchResponse batch, boolean attendanceMarkedToday, double syllabusCompletion) {
    }

    public record MentorDashboard(
            LocalDate date,
            List<BatchSnapshot> batches,
            long students,
            AttendanceToday attendance,
            List<Record> absentOrLateToday,
            List<TestResponse> recentTests,
            List<Card> pendingProgressCards,
            List<ExamResponse> upcomingExams,
            List<MeetingResponse> upcomingParentMeetings
    ) {
    }

    public record PendingMarks(String kind, Long id, String title, Ref batch, Ref subject, LocalDate date,
                               long entered, long students) {
    }

    public record SubjectSyllabus(Ref batch, Ref subject, int totalTopics, int completedTopics, double completion) {
    }

    public record FacultyDashboard(
            LocalDate date,
            List<ScheduleResponse> todaysClasses,
            List<ScheduleResponse> upcomingClasses,
            List<Ref> batches,
            List<Ref> subjects,
            List<PendingMarks> pendingMarks,
            List<SubjectSyllabus> syllabus,
            List<EntryExitResponse> todaysEntries
    ) {
    }

    // ---- Administrator's attendance and discipline dashboard -----------------------------------

    /**
     * Attendance and discipline for a period across the batches matching the filters (course,
     * batch, mentor): totals and the previous period of the same length, today's snapshot, the
     * trend (per day, or per week for periods over 45 days), each class, discipline by type and
     * week, absence reasons, the weekday pattern and every student with a risk level.
     */
    public record AdminAttendanceDashboard(
            LocalDate from, LocalDate to, LocalDate asOf,
            Long courseId, Long batchId, Long mentorId,
            long activeStudents,
            AttendanceSummary totals,
            AttendanceSummary previousTotals,
            TodaySnapshot today,
            long studentsAtRisk,
            String granularity,
            List<TrendPoint> trend,
            List<TrendPoint> previousTrend,
            List<ClassAttendance> classes,
            DisciplineTotals discipline,
            List<DisciplineWeek> disciplineWeeks,
            List<ReasonCount> absenceReasons,
            List<WeekdayRate> weekdays,
            List<StudentRisk> students
    ) {
    }

    /** Students by their most serious mark today; present includes late. */
    public record TodaySnapshot(long marked, long present, long late, long absent, long absentWithoutReason,
                                long notMarked) {
    }

    public record TrendPoint(LocalDate start, LocalDate end, String label, long present, long late, long absent,
                             long excused) {
    }

    public record ClassAttendance(Ref batch, Ref course, Ref mentor, long students, AttendanceSummary summary) {
    }

    /** Late arrivals and discipline records by type; {@code open} counts records not yet resolved. */
    public record DisciplineTotals(long late, long noUniform, long noIdTag, long other, long open) {
    }

    public record DisciplineWeek(LocalDate start, LocalDate end, String label, long late, long noUniform, long noIdTag,
                                 long other) {
    }

    public record ReasonCount(String key, String label, long count) {
    }

    public record WeekdayRate(DayOfWeek day, long marks, long attended) {
    }

    /** One student's attendance and discipline in the period, with the resulting risk level. */
    public record StudentRisk(Long studentId, String fullName, String admissionNumber, String photoUrl, Ref batch,
                              AttendanceSummary summary, long absences, long late, long noUniform, long noIdTag,
                              long otherIssues, long issues, Risk risk) {
    }

    public enum Risk { CRITICAL, AT_RISK, MONITOR, OK }

    /** One student's attendance: totals, one status per day (the most serious mark that day) and every mark. */
    public record StudentAttendanceDetail(Long studentId, String fullName, String admissionNumber, String photoUrl,
                                          Ref batch, LocalDate from, LocalDate to, AttendanceSummary summary,
                                          List<StudentDay> days, List<Record> records) {
    }

    public record StudentDay(LocalDate date, AttendanceStatus status, int marks) {
    }
}
