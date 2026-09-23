package com.coyotai.education.student.portal;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.coyotai.education.attendance.AttendanceStatus;
import com.coyotai.education.attendance.AttendanceSummary;
import com.coyotai.education.common.Ref;
import com.coyotai.education.schedule.ScheduleDtos.ScheduleResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Read-only views of a student's own records. */
public final class StudentPortalDtos {

    private StudentPortalDtos() {
    }

    public record AttendanceEntry(LocalDate date, Ref subject, AttendanceStatus status, String remarks) {
    }

    public record MyAttendance(LocalDate from, LocalDate to, AttendanceSummary summary, List<AttendanceEntry> history) {
    }

    public record TestResult(LocalDate date, String type, String title, Ref subject, Integer weekNumber,
                             BigDecimal marksObtained, BigDecimal maxMarks, boolean absent, Double percentage,
                             String grade, String remarks) {
    }

    public record ExamResult(LocalDate date, String examName, String examType, Ref subject, BigDecimal marksObtained,
                             BigDecimal maxMarks, BigDecimal passingMarks, boolean absent, Double percentage,
                             String grade, String result, String remarks) {
    }

    public record UpcomingExam(Long id, LocalDate date, @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                               @JsonFormat(pattern = "HH:mm") LocalTime endTime, String name, String examType,
                               Ref subject) {
    }

    public record MyExams(List<UpcomingExam> upcoming, List<ExamResult> results) {
    }

    public record Dashboard(
            String fullName,
            String admissionNumber,
            Ref course,
            Ref batch,
            Ref mentor,
            Ref academicYear,
            Double attendancePercentage,
            Double performance,
            String grade,
            BigDecimal pendingFines,
            BigDecimal feeBalance,
            BigDecimal nextDueAmount,
            LocalDate nextDueDate,
            List<UpcomingExam> upcomingExams,
            List<ScheduleResponse> todaysClasses,
            List<ExamResult> recentResults,
            long unreadNotifications,
            boolean feesEnabled
    ) {
    }
}
