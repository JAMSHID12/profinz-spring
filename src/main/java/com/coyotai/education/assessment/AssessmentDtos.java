package com.coyotai.education.assessment;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.coyotai.education.common.Ref;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class AssessmentDtos {

    private AssessmentDtos() {
    }

    public record TestRequest(
            @NotNull(message = "Test type is required") AcademicTest.Type testType,
            @NotBlank(message = "Title is required") @Size(max = 150) String title,
            @NotNull(message = "Batch is required") Long batchId,
            @NotNull(message = "Subject is required") Long subjectId,
            @NotNull(message = "Test date is required") LocalDate testDate,
            @Min(value = 1, message = "Week must be 1-53") @Max(value = 53, message = "Week must be 1-53") Integer weekNumber,
            @NotNull(message = "Maximum marks are required")
            @DecimalMin(value = "0.01", message = "Maximum marks must be greater than zero")
            @Digits(integer = 5, fraction = 2) BigDecimal maxMarks,
            @Size(max = 500) String remarks
    ) {
    }

    public record TestResponse(Long id, AcademicTest.Type testType, String title, Ref batch, Ref subject,
                               LocalDate testDate, Integer weekNumber, BigDecimal maxMarks, PublicationStatus status,
                               String remarks, Instant publishedAt, long resultCount) {

        static TestResponse from(AcademicTest t, long resultCount) {
            return new TestResponse(t.getId(), t.getTestType(), t.getTitle(),
                    Ref.of(t.getBatch().getId(), t.getBatch().getName()),
                    Ref.of(t.getSubject().getId(), t.getSubject().getName()),
                    t.getTestDate(), t.getWeekNumber(), t.getMaxMarks(), t.getStatus(), t.getRemarks(),
                    t.getPublishedAt(), resultCount);
        }
    }

    public record ExamRequest(
            @NotBlank(message = "Exam name is required") @Size(max = 150) String name,
            @NotNull(message = "Exam type is required") Long examTypeId,
            @NotNull(message = "Batch is required") Long batchId,
            @NotNull(message = "Subject is required") Long subjectId,
            @NotNull(message = "Exam date is required") LocalDate examDate,
            @JsonFormat(pattern = "HH:mm[:ss]") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm[:ss]") LocalTime endTime,
            @NotNull(message = "Maximum marks are required")
            @DecimalMin(value = "0.01", message = "Maximum marks must be greater than zero")
            @Digits(integer = 5, fraction = 2) BigDecimal maxMarks,
            @NotNull(message = "Passing marks are required")
            @DecimalMin(value = "0", message = "Passing marks cannot be negative")
            @Digits(integer = 5, fraction = 2) BigDecimal passingMarks,
            Long facultyId,
            @Size(max = 500) String remarks
    ) {
    }

    public record ExamResponse(Long id, String name, Ref examType, Ref course, Ref batch, Ref subject,
                               LocalDate examDate, @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                               @JsonFormat(pattern = "HH:mm") LocalTime endTime, BigDecimal maxMarks,
                               BigDecimal passingMarks, Ref faculty, PublicationStatus status, String remarks,
                               Instant publishedAt, long resultCount) {

        static ExamResponse from(Exam e, long resultCount) {
            return new ExamResponse(e.getId(), e.getName(),
                    Ref.of(e.getExamType().getId(), e.getExamType().getName()),
                    Ref.of(e.getCourse().getId(), e.getCourse().getName()),
                    Ref.of(e.getBatch().getId(), e.getBatch().getName()),
                    Ref.of(e.getSubject().getId(), e.getSubject().getName()),
                    e.getExamDate(), e.getStartTime(), e.getEndTime(), e.getMaxMarks(), e.getPassingMarks(),
                    e.getFaculty() == null ? null : Ref.of(e.getFaculty().getId(), e.getFaculty().getFullName()),
                    e.getStatus(), e.getRemarks(), e.getPublishedAt(), resultCount);
        }
    }

    public record MarksRow(Long studentId, String admissionNumber, String fullName, BigDecimal marksObtained,
                           boolean absent, String grade, String remarks) {
    }

    public record MarksSheet(Long assessmentId, String title, Ref batch, Ref subject, LocalDate date,
                             BigDecimal maxMarks, BigDecimal passingMarks, PublicationStatus status,
                             boolean editable, List<MarksRow> rows) {
    }

    public record MarksEntryRequest(@NotEmpty(message = "Enter marks for at least one student") @Valid List<Entry> entries) {

        public record Entry(
                @NotNull(message = "Student is required") Long studentId,
                @DecimalMin(value = "0", message = "Marks cannot be negative") @Digits(integer = 5, fraction = 2)
                BigDecimal marksObtained,
                boolean absent,
                @Size(max = 255) String remarks
        ) {
        }
    }

    public record StatusRequest(@NotNull(message = "Target status is required") PublicationStatus status) {
    }

    public record ExamTypeRequest(
            @NotBlank(message = "Code is required") @Size(max = 30) String code,
            @NotBlank(message = "Name is required") @Size(max = 100) String name,
            Integer displayOrder,
            Boolean active
    ) {
    }

    public record ExamTypeResponse(Long id, String code, String name, int displayOrder, boolean active) {

        static ExamTypeResponse from(ExamType type) {
            return new ExamTypeResponse(type.getId(), type.getCode(), type.getName(), type.getDisplayOrder(),
                    type.isActive());
        }
    }
}
