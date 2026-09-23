package com.coyotai.education.academic;

import com.coyotai.education.common.RecordStatus;
import com.coyotai.education.common.Ref;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Request and response shapes for academic master data. */
public final class AcademicDtos {

    private AcademicDtos() {
    }

    private static final String CODE_PATTERN = "^[A-Za-z0-9._-]+$";
    private static final String CODE_MESSAGE = "Code may contain letters, digits, dot, dash and underscore";

    public record CourseRequest(
            @NotBlank(message = "Course code is required")
            @Size(max = 30, message = "Code is too long")
            @Pattern(regexp = CODE_PATTERN, message = CODE_MESSAGE)
            String code,
            @NotBlank(message = "Course name is required") @Size(max = 150) String name,
            @Size(max = 500) String description,
            RecordStatus status,
            @Min(value = 0, message = "Display order cannot be negative") Integer displayOrder,
            @DecimalMin(value = "0", message = "The course fee cannot be negative")
            @Digits(integer = 10, fraction = 2, message = "Enter the fee with at most 2 decimals")
            BigDecimal feeAmount,
            @Min(value = 1, message = "At least one installment")
            @Max(value = 36, message = "At most 36 installments")
            Integer defaultInstallments
    ) {
    }

    public record CourseResponse(Long id, String code, String name, String description, RecordStatus status,
                                 int displayOrder, BigDecimal feeAmount, Integer defaultInstallments,
                                 long subjectCount) {

        static CourseResponse from(Course course, long subjectCount) {
            return new CourseResponse(course.getId(), course.getCode(), course.getName(), course.getDescription(),
                    course.getStatus(), course.getDisplayOrder(), course.getFeeAmount(),
                    course.getDefaultInstallments(), subjectCount);
        }
    }

    public record SubjectRequest(
            @NotNull(message = "Course is required") Long courseId,
            @NotBlank(message = "Subject code is required")
            @Size(max = 30, message = "Code is too long")
            @Pattern(regexp = CODE_PATTERN, message = CODE_MESSAGE)
            String code,
            @NotBlank(message = "Subject name is required") @Size(max = 150) String name,
            @Size(max = 500) String description,
            RecordStatus status,
            @Min(value = 0, message = "Display order cannot be negative") Integer displayOrder
    ) {
    }

    public record SubjectResponse(Long id, Ref course, String code, String name, String description,
                                  RecordStatus status, int displayOrder) {

        public static SubjectResponse from(Subject subject) {
            return new SubjectResponse(subject.getId(),
                    Ref.of(subject.getCourse().getId(), subject.getCourse().getName()),
                    subject.getCode(), subject.getName(), subject.getDescription(),
                    subject.getStatus(), subject.getDisplayOrder());
        }
    }

    public record AcademicYearRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 30)
            @Pattern(regexp = "^[0-9]{4}-[0-9]{4}$", message = "Use the format 2026-2027")
            String name,
            @NotNull(message = "Start date is required") LocalDate startDate,
            @NotNull(message = "End date is required") LocalDate endDate,
            Boolean current,
            AcademicYear.Status status
    ) {
    }

    public record AcademicYearResponse(Long id, String name, LocalDate startDate, LocalDate endDate,
                                       boolean current, AcademicYear.Status status) {

        static AcademicYearResponse from(AcademicYear year) {
            return new AcademicYearResponse(year.getId(), year.getName(), year.getStartDate(), year.getEndDate(),
                    year.isCurrent(), year.getStatus());
        }
    }

    public record BatchRequest(
            @NotBlank(message = "Batch name is required") @Size(max = 120) String name,
            @NotNull(message = "Course is required") Long courseId,
            @NotNull(message = "Academic year is required") Long academicYearId,
            Long mentorId,
            LocalDate startDate,
            LocalDate endDate,
            @Min(value = 1, message = "Capacity must be at least 1") Integer capacity,
            Batch.Status status,
            @Size(max = 500) String description
    ) {
    }

    public record BatchResponse(Long id, String name, Ref course, Ref academicYear, Ref mentor,
                                LocalDate startDate, LocalDate endDate, Integer capacity, Batch.Status status,
                                String description, long studentCount) {

        public static BatchResponse from(Batch batch, long studentCount) {
            return new BatchResponse(batch.getId(), batch.getName(),
                    Ref.of(batch.getCourse().getId(), batch.getCourse().getName()),
                    Ref.of(batch.getAcademicYear().getId(), batch.getAcademicYear().getName()),
                    batch.getMentor() == null ? null : Ref.of(batch.getMentor().getId(), batch.getMentor().getFullName()),
                    batch.getStartDate(), batch.getEndDate(), batch.getCapacity(), batch.getStatus(),
                    batch.getDescription(), studentCount);
        }
    }
}
