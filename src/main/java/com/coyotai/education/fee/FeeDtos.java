package com.coyotai.education.fee;

import com.coyotai.education.common.Ref;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class FeeDtos {

    private FeeDtos() {
    }

    /**
     * A fee plan for one student. The fee is always the course fee from the course master -
     * it is never typed in here; the student's own discount (and its reason) is what varies.
     * The net amount is split into {@code installmentCount} monthly installments (default:
     * the course's default number of installments) starting on {@code firstDueDate}.
     */
    public record PlanRequest(
            @NotNull(message = "Student is required") Long studentId,
            /* Defaults to the student's course and academic year. */
            Long courseId,
            Long academicYearId,
            @DecimalMin(value = "0", message = "Discount cannot be negative")
            @Digits(integer = 10, fraction = 2, message = "Enter the discount with at most 2 decimals")
            BigDecimal discountAmount,
            @Size(max = 255, message = "Keep the reason under 255 characters") String discountReason,
            @Min(value = 1, message = "At least one installment") @Max(value = 36, message = "At most 36 installments")
            Integer installmentCount,
            @NotNull(message = "First due date is required") LocalDate firstDueDate,
            /* Optional; defaults to "<course> <academic year>". */
            @Size(max = 150) String title,
            @Size(max = 500) String notes
    ) {
    }

    /** One student's discount in a batch-wide plan. */
    public record StudentDiscount(
            @NotNull(message = "Student is required") Long studentId,
            @DecimalMin(value = "0", message = "Discount cannot be negative")
            @Digits(integer = 10, fraction = 2, message = "Enter the discount with at most 2 decimals")
            BigDecimal discountAmount,
            @Size(max = 255, message = "Keep the reason under 255 characters") String reason
    ) {
    }

    /** Fee plans at the batch's course fee for every active student, each with their own discount. */
    public record BulkPlanRequest(
            @NotNull(message = "Batch is required") Long batchId,
            @Min(value = 1, message = "At least one installment") @Max(value = 36, message = "At most 36 installments")
            Integer installmentCount,
            @NotNull(message = "First due date is required") LocalDate firstDueDate,
            @Size(max = 150) String title,
            @Size(max = 500) String notes,
            @Valid List<StudentDiscount> discounts
    ) {
    }

    public record BulkPlanResult(List<PlanResponse> created, int skipped) {
    }

    /** A new discount for an existing plan; the unpaid balance is re-divided over the open installments. */
    public record DiscountRequest(
            @NotNull(message = "Discount is required")
            @DecimalMin(value = "0", message = "Discount cannot be negative")
            @Digits(integer = 10, fraction = 2, message = "Enter the discount with at most 2 decimals")
            BigDecimal discountAmount,
            @Size(max = 255, message = "Keep the reason under 255 characters") String reason
    ) {
    }

    /** Amounts follow from the course fee and discount; only the label and due date are edited directly. */
    public record InstallmentUpdateRequest(
            @NotBlank(message = "Installment label is required") @Size(max = 100) String label,
            @NotNull(message = "Due date is required") LocalDate dueDate
    ) {
    }

    public record PlanUpdateRequest(@NotBlank @Size(max = 150) String title, StudentFee.Status status,
                                    @Size(max = 500) String notes) {
    }

    public record InstallmentResponse(Long id, Long planId, String planTitle, Ref student, String admissionNumber,
                                      Ref batch, int installmentNo, String label, LocalDate dueDate, BigDecimal amount,
                                      BigDecimal paidAmount, BigDecimal pendingAmount, InstallmentStatus status,
                                      int reminderCount, LocalDate lastReminderDate) {

        public static InstallmentResponse from(FeeInstallment i) {
            var student = i.getStudent();
            return new InstallmentResponse(i.getId(), i.getStudentFee().getId(), i.getStudentFee().getTitle(),
                    Ref.of(student.getId(), student.getFullName()), student.getAdmissionNumber(),
                    student.getBatch() == null ? null : Ref.of(student.getBatch().getId(), student.getBatch().getName()),
                    i.getInstallmentNo(), i.getLabel(), i.getDueDate(), i.getAmount(), i.getPaidAmount(),
                    i.getPendingAmount(), i.getStatus(), i.getReminderCount(), i.getLastReminderDate());
        }
    }

    /** {@code totalAmount} is the course fee the plan was created with; net = total - discount. */
    public record PlanResponse(Long id, Ref student, String admissionNumber, String title, Ref course, Ref academicYear,
                               BigDecimal totalAmount, BigDecimal discountAmount, String discountReason,
                               BigDecimal netAmount, BigDecimal paidAmount, BigDecimal outstandingAmount,
                               StudentFee.Status status, String notes, List<InstallmentResponse> installments) {
    }

    public record PaymentRequest(
            @NotNull(message = "Installment is required") Long installmentId,
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.01", message = "Payment amount must be greater than zero")
            @Digits(integer = 10, fraction = 2) BigDecimal amount,
            LocalDate paymentDate,
            PaymentMethod paymentMethod,
            @Size(max = 60) String referenceNumber,
            @Size(max = 255) String notes,
            Boolean allowOverpayment,
            Boolean notifyParent
    ) {
    }

    public record PaymentResponse(Long id, String receiptNumber, Ref student, String admissionNumber,
                                  Long installmentId, String installmentLabel, String planTitle, BigDecimal amount,
                                  LocalDate paymentDate, PaymentMethod paymentMethod, String referenceNumber,
                                  String notes, Instant createdAt) {

        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getReceiptNumber(),
                    Ref.of(p.getStudent().getId(), p.getStudent().getFullName()), p.getStudent().getAdmissionNumber(),
                    p.getInstallment().getId(), p.getInstallment().getLabel(), p.getInstallment().getStudentFee().getTitle(),
                    p.getAmount(), p.getPaymentDate(), p.getPaymentMethod(), p.getReferenceNumber(), p.getNotes(),
                    p.getCreatedAt());
        }
    }

    /** Printable receipt. The centre name comes from configuration. */
    public record Receipt(String centreName, String receiptNumber, LocalDate paymentDate, String studentName,
                          String admissionNumber, String planTitle, String installmentLabel, BigDecimal amount,
                          PaymentMethod paymentMethod, String referenceNumber, BigDecimal installmentBalance,
                          String currency) {
    }

    /** A student's complete fee position: what the portal and the student page show. */
    public record FeeSummary(BigDecimal totalFee, BigDecimal discount, BigDecimal netFee, BigDecimal paid,
                             BigDecimal outstanding, BigDecimal overdue, BigDecimal nextDueAmount,
                             LocalDate nextDueDate, List<PlanResponse> plans, List<PaymentResponse> payments) {
    }
}
