package com.coyotai.education.fee;

import com.coyotai.education.common.ApiResponse;
import com.coyotai.education.common.PageResponse;
import com.coyotai.education.fee.FeeDtos.BulkPlanRequest;
import com.coyotai.education.fee.FeeDtos.BulkPlanResult;
import com.coyotai.education.fee.FeeDtos.DiscountRequest;
import com.coyotai.education.fee.FeeDtos.FeeSummary;
import com.coyotai.education.fee.FeeDtos.InstallmentResponse;
import com.coyotai.education.fee.FeeDtos.InstallmentUpdateRequest;
import com.coyotai.education.fee.FeeDtos.PaymentRequest;
import com.coyotai.education.fee.FeeDtos.PaymentResponse;
import com.coyotai.education.fee.FeeDtos.PlanRequest;
import com.coyotai.education.fee.FeeDtos.PlanResponse;
import com.coyotai.education.fee.FeeDtos.PlanUpdateRequest;
import com.coyotai.education.fee.FeeDtos.Receipt;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.RequiresModule;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Fee plans, installments, payments and receipts. */
@RestController
@RequestMapping("/api")
@RequiresModule(ModuleCode.FEES)
public class FeeController {

    private final FeeService feeService;
    private final PaymentService paymentService;

    public FeeController(FeeService feeService, PaymentService paymentService) {
        this.feeService = feeService;
        this.paymentService = paymentService;
    }

    @GetMapping("/fees/plans")
    @PreAuthorize("hasAuthority('FEE_VIEW')")
    public ApiResponse<List<PlanResponse>> plans(@RequestParam(required = false) Long studentId) {
        return ApiResponse.ok(feeService.plans(studentId));
    }

    @GetMapping("/fees/plans/{id}")
    @PreAuthorize("hasAuthority('FEE_VIEW')")
    public ApiResponse<PlanResponse> plan(@PathVariable Long id) {
        return ApiResponse.ok(feeService.plan(id));
    }

    @PostMapping("/fees/plans")
    @PreAuthorize("hasAuthority('FEE_MANAGE')")
    public ApiResponse<PlanResponse> createPlan(@Valid @RequestBody PlanRequest request) {
        return ApiResponse.ok(feeService.createPlan(request), "Fee plan created");
    }

    @PostMapping("/fees/plans/bulk")
    @PreAuthorize("hasAuthority('FEE_MANAGE')")
    public ApiResponse<BulkPlanResult> createPlansForBatch(@Valid @RequestBody BulkPlanRequest request) {
        BulkPlanResult result = feeService.createPlansForBatch(request);
        return ApiResponse.ok(result, result.created().size() + " fee plans created"
                + (result.skipped() > 0 ? ", " + result.skipped() + " students already had one" : ""));
    }

    @PutMapping("/fees/plans/{id}")
    @PreAuthorize("hasAuthority('FEE_MANAGE')")
    public ApiResponse<PlanResponse> updatePlan(@PathVariable Long id, @Valid @RequestBody PlanUpdateRequest request) {
        return ApiResponse.ok(feeService.updatePlan(id, request), "Fee plan updated");
    }

    /** A different discount for one student; the unpaid balance is re-divided over the open installments. */
    @PutMapping("/fees/plans/{id}/discount")
    @PreAuthorize("hasAuthority('FEE_MANAGE')")
    public ApiResponse<PlanResponse> changeDiscount(@PathVariable Long id, @Valid @RequestBody DiscountRequest request) {
        return ApiResponse.ok(feeService.changeDiscount(id, request), "Discount updated");
    }

    @GetMapping("/fees/installments")
    @PreAuthorize("hasAuthority('FEE_VIEW')")
    public ApiResponse<List<InstallmentResponse>> installments(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) InstallmentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(feeService.installments(studentId, batchId, status, from, to));
    }

    @PutMapping("/fees/installments/{id}")
    @PreAuthorize("hasAuthority('FEE_MANAGE')")
    public ApiResponse<InstallmentResponse> updateInstallment(@PathVariable Long id,
                                                              @Valid @RequestBody InstallmentUpdateRequest request) {
        return ApiResponse.ok(feeService.updateInstallment(id, request), "Installment updated");
    }

    @PostMapping("/fees/installments/{id}/waive")
    @PreAuthorize("hasAuthority('FEE_MANAGE')")
    public ApiResponse<InstallmentResponse> waive(@PathVariable Long id) {
        return ApiResponse.ok(feeService.waive(id), "Installment waived");
    }

    @PostMapping("/fees/installments/{id}/reminder")
    @PreAuthorize("hasAuthority('FEE_MANAGE')")
    public ApiResponse<Void> reminder(@PathVariable Long id) {
        feeService.sendManualReminder(id);
        return ApiResponse.ok(null, "Reminder queued");
    }

    @GetMapping("/fees/student/{studentId}")
    @PreAuthorize("hasAuthority('FEE_VIEW')")
    public ApiResponse<FeeSummary> studentSummary(@PathVariable Long studentId) {
        return ApiResponse.ok(feeService.summary(studentId));
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('FEE_VIEW')")
    public ApiResponse<PageResponse<PaymentResponse>> payments(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ApiResponse.ok(paymentService.search(studentId, from, to, PageRequest.of(page, Math.min(size, 200))));
    }

    @GetMapping("/payments/student/{studentId}")
    @PreAuthorize("hasAuthority('FEE_VIEW')")
    public ApiResponse<List<PaymentResponse>> studentPayments(@PathVariable Long studentId) {
        return ApiResponse.ok(paymentService.forStudent(studentId));
    }

    @PostMapping("/payments")
    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    public ApiResponse<PaymentResponse> record(@Valid @RequestBody PaymentRequest request) {
        return ApiResponse.ok(paymentService.record(request), "Payment recorded");
    }

    @GetMapping("/payments/{id}/receipt")
    @PreAuthorize("hasAuthority('FEE_VIEW')")
    public ApiResponse<Receipt> receipt(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.receipt(id));
    }
}
