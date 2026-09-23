package com.coyotai.education.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.coyotai.education.common.Ref;
import com.coyotai.education.schedule.ScheduleDtos.ScheduleResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class AttendanceDtos {

    private AttendanceDtos() {
    }

    /**
     * Something the current mentor or faculty member can take attendance for on a date: a
     * batch's whole day ({@code scheduleId} null) or one class.
     */
    public record TakerClass(Ref batch, Long scheduleId, Ref subject,
                             @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                             @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                             String room, Ref faculty, int students, int marked) {
    }

    public record SheetRow(Long attendanceId, Long studentId, String admissionNumber, String fullName, String photoUrl,
                           AttendanceStatus status, Integer lateMinutes, AbsenceReason absenceReason,
                           boolean noUniform, boolean noIdTag, String remarks, boolean parentNotifiable) {
    }

    /**
     * The marking sheet. {@code canMark} says whether the current user may save it; rows
     * without a status have not been marked yet (the screen treats them as present).
     */
    public record Sheet(Ref batch, LocalDate date, ScheduleResponse schedule, boolean alreadyMarked,
                        Instant markedAt, String markedBy, boolean canMark,
                        List<ScheduleResponse> classesThatDay, List<SheetRow> rows) {
    }

    /** One request saves the whole sheet: a batch, a date and optionally one scheduled class. */
    public record BulkRequest(
            @NotNull(message = "Batch is required") Long batchId,
            @NotNull(message = "Date is required") LocalDate date,
            Long scheduleId,
            @NotEmpty(message = "Mark at least one student") @Valid List<Entry> entries
    ) {

        public record Entry(
                @NotNull(message = "Student is required") Long studentId,
                @NotNull(message = "Status is required") AttendanceStatus status,
                @Min(value = 1, message = "Late by at least 1 minute")
                @Max(value = 600, message = "Late by at most 600 minutes") Integer lateMinutes,
                AbsenceReason absenceReason,
                Boolean noUniform,
                Boolean noIdTag,
                @Size(max = 255, message = "Keep the note under 255 characters") String remarks
        ) {
        }
    }

    public record BulkResult(int saved, int created, int updated, int notificationsQueued, int notificationsSkipped,
                             int observationsRecorded) {
    }

    /** A correction of one mark. It replaces the mark: details left out are cleared. */
    public record UpdateRequest(@NotNull(message = "Status is required") AttendanceStatus status,
                                @Min(value = 1, message = "Late by at least 1 minute")
                                @Max(value = 600, message = "Late by at most 600 minutes") Integer lateMinutes,
                                AbsenceReason absenceReason,
                                Boolean noUniform,
                                Boolean noIdTag,
                                @Size(max = 255, message = "Keep the note under 255 characters") String remarks) {
    }

    /** One mark. {@code canCorrect} says whether the current user may correct it (see {@link AttendanceAccess}). */
    public record Record(Long id, Ref student, String admissionNumber, Ref batch, Ref subject, LocalDate date,
                         AttendanceStatus status, Integer lateMinutes, AbsenceReason absenceReason, boolean noUniform,
                         boolean noIdTag, String remarks, Instant markedAt,
                         Attendance.NotificationDecision notificationStatus, boolean canCorrect) {

        public static Record from(Attendance a) {
            return from(a, false);
        }

        public static Record from(Attendance a, boolean canCorrect) {
            var schedule = a.getClassSchedule();
            return new Record(a.getId(),
                    Ref.of(a.getStudent().getId(), a.getStudent().getFullName()),
                    a.getStudent().getAdmissionNumber(),
                    Ref.of(a.getBatch().getId(), a.getBatch().getName()),
                    schedule == null ? null : Ref.of(schedule.getSubject().getId(), schedule.getSubject().getName()),
                    a.getAttendanceDate(), a.getStatus(), a.getLateMinutes(), a.getAbsenceReason(), a.isNoUniform(),
                    a.isNoIdTag(), a.getRemarks(), a.getMarkedAt(), a.getNotificationStatus(), canCorrect);
        }
    }
}
