package com.coyotai.education.schedule;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.coyotai.education.common.Ref;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    public record ScheduleRequest(
            @NotNull(message = "Batch is required") Long batchId,
            @NotNull(message = "Subject is required") Long subjectId,
            Long facultyId,
            @NotNull(message = "Date is required") LocalDate scheduleDate,
            @NotNull(message = "Start time is required") @JsonFormat(pattern = "HH:mm[:ss]") LocalTime startTime,
            @NotNull(message = "End time is required") @JsonFormat(pattern = "HH:mm[:ss]") LocalTime endTime,
            @Size(max = 60) String room,
            @Size(max = 500) String notes,
            /* Optional: repeat on the same weekday until this date (inclusive). */
            LocalDate repeatWeeklyUntil,
            ClassSchedule.Status status
    ) {
    }

    public record ScheduleResponse(Long id, Ref batch, Ref course, Ref subject, Ref faculty, LocalDate scheduleDate,
                                   @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                   @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                                   String room, ClassSchedule.Status status, String notes) {

        public static ScheduleResponse from(ClassSchedule s) {
            return new ScheduleResponse(s.getId(),
                    Ref.of(s.getBatch().getId(), s.getBatch().getName()),
                    Ref.of(s.getCourse().getId(), s.getCourse().getName()),
                    Ref.of(s.getSubject().getId(), s.getSubject().getName()),
                    s.getFaculty() == null ? null : Ref.of(s.getFaculty().getId(), s.getFaculty().getFullName()),
                    s.getScheduleDate(), s.getStartTime(), s.getEndTime(), s.getRoom(), s.getStatus(), s.getNotes());
        }
    }

    public enum ConflictType { FACULTY, BATCH, ROOM }

    public record Conflict(ConflictType type, LocalDate date, String message, ScheduleResponse existing) {
    }

    public record RegisterRequest(
            @NotNull(message = "Class is required") Long scheduleId,
            @NotNull(message = "Actual start is required") @JsonFormat(pattern = "HH:mm[:ss]") LocalTime actualStart,
            @NotNull(message = "Actual end is required") @JsonFormat(pattern = "HH:mm[:ss]") LocalTime actualEnd,
            @NotBlank(message = "Topic covered is required") @Size(max = 500) String topicCovered,
            @Min(value = 0, message = "Student count cannot be negative") Integer studentCount,
            @Size(max = 500) String remarks
    ) {
    }

    public record RegisterResponse(Long id, ScheduleResponse schedule, Ref faculty,
                                   @JsonFormat(pattern = "HH:mm") LocalTime actualStart,
                                   @JsonFormat(pattern = "HH:mm") LocalTime actualEnd,
                                   String topicCovered, Integer studentCount, String remarks) {

        static RegisterResponse from(ClassRegisterEntry e) {
            return new RegisterResponse(e.getId(), ScheduleResponse.from(e.getClassSchedule()),
                    e.getFaculty() == null ? null : Ref.of(e.getFaculty().getId(), e.getFaculty().getFullName()),
                    e.getActualStart(), e.getActualEnd(), e.getTopicCovered(), e.getStudentCount(), e.getRemarks());
        }
    }

    public record EntryExitRequest(
            Long facultyId,
            @NotNull(message = "Date is required") LocalDate entryDate,
            @NotBlank(message = "Session is required") @Size(max = 40) String sessionLabel,
            @NotNull(message = "Entry time is required") @JsonFormat(pattern = "HH:mm[:ss]") LocalTime entryTime,
            @JsonFormat(pattern = "HH:mm[:ss]") LocalTime exitTime,
            @Size(max = 255) String remarks
    ) {
    }

    public record EntryExitResponse(Long id, Ref faculty, LocalDate entryDate, String sessionLabel,
                                    @JsonFormat(pattern = "HH:mm") LocalTime entryTime,
                                    @JsonFormat(pattern = "HH:mm") LocalTime exitTime, String remarks) {

        static EntryExitResponse from(FacultyEntryExit e) {
            return new EntryExitResponse(e.getId(), Ref.of(e.getFaculty().getId(), e.getFaculty().getFullName()),
                    e.getEntryDate(), e.getSessionLabel(), e.getEntryTime(), e.getExitTime(), e.getRemarks());
        }
    }

    public record CreateResult(List<ScheduleResponse> created) {
    }
}
