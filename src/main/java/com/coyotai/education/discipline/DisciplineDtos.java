package com.coyotai.education.discipline;

import com.coyotai.education.common.Ref;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class DisciplineDtos {

    private DisciplineDtos() {
    }

    public record RecordRequest(
            @NotNull(message = "Student is required") Long studentId,
            @NotNull(message = "Discipline type is required") Long disciplineTypeId,
            @NotNull(message = "Date is required") LocalDate incidentDate,
            @NotBlank(message = "Description is required") @Size(max = 1000) String description,
            @Size(max = 500) String actionTaken,
            DisciplineRecord.Status status,
            /* Optionally raise a fine in the same step. */
            @DecimalMin(value = "0.01", message = "Fine amount must be greater than zero")
            @Digits(integer = 10, fraction = 2) BigDecimal fineAmount,
            LocalDate fineDueDate
    ) {
    }

    public record RecordResponse(Long id, Ref student, String admissionNumber, Ref batch, Ref disciplineType,
                                 LocalDate incidentDate, String description, String actionTaken,
                                 DisciplineRecord.Status status) {

        public static RecordResponse from(DisciplineRecord d) {
            return new RecordResponse(d.getId(), Ref.of(d.getStudent().getId(), d.getStudent().getFullName()),
                    d.getStudent().getAdmissionNumber(),
                    d.getBatch() == null ? null : Ref.of(d.getBatch().getId(), d.getBatch().getName()),
                    Ref.of(d.getDisciplineType().getId(), d.getDisciplineType().getName()),
                    d.getIncidentDate(), d.getDescription(), d.getActionTaken(), d.getStatus());
        }
    }

    public record FineRequest(
            @NotNull(message = "Student is required") Long studentId,
            @NotBlank(message = "Reason is required") @Size(max = 255) String reason,
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.01", message = "Fine amount must be greater than zero")
            @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull(message = "Fine date is required") LocalDate fineDate,
            LocalDate dueDate,
            @Size(max = 500) String remarks
    ) {
    }

    public record FineStatusRequest(
            @NotNull(message = "Status is required") StudentFine.Status status,
            LocalDate paidDate,
            @Size(max = 60) String paymentReference,
            @Size(max = 500) String remarks
    ) {
    }

    public record FineResponse(Long id, Ref student, String admissionNumber, Ref batch, String reason,
                               BigDecimal amount, LocalDate fineDate, LocalDate dueDate, StudentFine.Status status,
                               LocalDate paidDate, String paymentReference, String remarks) {

        public static FineResponse from(StudentFine f) {
            var batch = f.getStudent().getBatch();
            return new FineResponse(f.getId(), Ref.of(f.getStudent().getId(), f.getStudent().getFullName()),
                    f.getStudent().getAdmissionNumber(),
                    batch == null ? null : Ref.of(batch.getId(), batch.getName()),
                    f.getReason(), f.getAmount(), f.getFineDate(), f.getDueDate(), f.getStatus(), f.getPaidDate(),
                    f.getPaymentReference(), f.getRemarks());
        }
    }

    public record TypeRequest(
            @NotBlank(message = "Code is required") @Size(max = 30) String code,
            @NotBlank(message = "Name is required") @Size(max = 100) String name,
            @DecimalMin(value = "0", message = "Default fine cannot be negative") BigDecimal defaultFineAmount,
            Integer displayOrder,
            Boolean active
    ) {
    }

    public record TypeResponse(Long id, String code, String name, BigDecimal defaultFineAmount, int displayOrder,
                               boolean active) {

        public static TypeResponse from(DisciplineType t) {
            return new TypeResponse(t.getId(), t.getCode(), t.getName(), t.getDefaultFineAmount(), t.getDisplayOrder(),
                    t.isActive());
        }
    }
}
