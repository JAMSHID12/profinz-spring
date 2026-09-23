package com.coyotai.education.fee;

import com.coyotai.education.audit.AuditService;
import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.common.PageResponse;
import com.coyotai.education.common.ResourceNotFoundException;
import com.coyotai.education.fee.FeeDtos.PaymentRequest;
import com.coyotai.education.fee.FeeDtos.PaymentResponse;
import com.coyotai.education.fee.FeeDtos.Receipt;
import com.coyotai.education.notification.NotificationEvent;
import com.coyotai.education.notification.NotificationMessageFactory;
import com.coyotai.education.notification.NotificationService;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.student.Student;
import com.coyotai.education.util.Money;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Records payments. In one transaction: save the payment (with its receipt number), update the
 * installment, recompute pending and status, and optionally queue a confirmation.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final FeeService feeService;
    private final NotificationService notificationService;
    private final NotificationMessageFactory messageFactory;
    private final ProjectConfigService configService;
    private final AuditService auditService;

    public PaymentService(PaymentRepository paymentRepository, FeeService feeService,
                          NotificationService notificationService, NotificationMessageFactory messageFactory,
                          ProjectConfigService configService, AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.feeService = feeService;
        this.notificationService = notificationService;
        this.messageFactory = messageFactory;
        this.configService = configService;
        this.auditService = auditService;
    }

    @Transactional
    public PaymentResponse record(PaymentRequest request) {
        FeeInstallment installment = feeService.getInstallment(request.installmentId());
        if (installment.getStatus() == InstallmentStatus.WAIVED) {
            throw new BusinessRuleException("This installment was waived");
        }
        if (installment.getStudentFee().getStatus() != StudentFee.Status.ACTIVE) {
            throw new BusinessRuleException("Payments can only be recorded against an active fee plan");
        }
        BigDecimal amount = Money.scale(request.amount());
        if (!Money.isPositive(amount)) {
            throw new BusinessRuleException("Payment amount must be greater than zero");
        }
        if (!Boolean.TRUE.equals(request.allowOverpayment()) && amount.compareTo(installment.getPendingAmount()) > 0) {
            throw new BusinessRuleException("Payment exceeds the pending amount of "
                    + installment.getPendingAmount().toPlainString() + ". Allow overpayment to record it anyway.");
        }
        LocalDate paymentDate = request.paymentDate() == null ? configService.today() : request.paymentDate();
        if (paymentDate.isAfter(configService.today())) {
            throw new BusinessRuleException("The payment date cannot be in the future");
        }

        Payment payment = new Payment();
        payment.setInstallment(installment);
        payment.setStudent(installment.getStudent());
        payment.setAmount(amount);
        payment.setPaymentDate(paymentDate);
        payment.setPaymentMethod(request.paymentMethod() == null ? PaymentMethod.CASH : request.paymentMethod());
        payment.setReferenceNumber(blankToNull(request.referenceNumber()));
        payment.setNotes(blankToNull(request.notes()));
        payment.setReceiptNumber(nextReceiptNumber(paymentDate.getYear()));
        paymentRepository.save(payment);

        FeeInstallment updated = feeService.applyPayment(installment, amount);
        auditService.record("Payment", payment.getId(), AuditService.CREATE,
                "Receipt " + payment.getReceiptNumber() + ": " + amount.toPlainString() + " from "
                        + installment.getStudent().getFullName() + " for \"" + installment.getLabel() + "\"");

        if (request.notifyParent() == null || request.notifyParent()) {
            Student student = installment.getStudent();
            String parentName = student.getParent() == null ? "Parent" : student.getParent().getName();
            notificationService.publish(NotificationEvent.PAYMENT_RECEIVED, student,
                    messageFactory.paymentReceived(parentName, student.getFullName(), amount, installment.getLabel(),
                            updated.getPendingAmount(), payment.getReceiptNumber()));
        }
        return PaymentResponse.from(payment);
    }

    private String nextReceiptNumber(int year) {
        String prefix = configService.receiptPrefix();
        String series = prefix + "-RCPT-" + year + "-";
        return FeeCalculator.nextReceiptNumber(prefix, year, paymentRepository.findMaxReceiptNumber(series).orElse(null));
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> search(Long studentId, LocalDate from, LocalDate to, Pageable pageable) {
        LocalDate end = to == null ? configService.today() : to;
        LocalDate start = from == null ? end.minusMonths(3) : from;
        return PageResponse.of(paymentRepository.search(studentId, start, end, pageable), PaymentResponse::from);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> forStudent(Long studentId) {
        return paymentRepository.findForStudent(studentId).stream().map(PaymentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Receipt receipt(Long paymentId) {
        Payment payment = paymentRepository.findDetail(paymentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Payment", paymentId));
        return toReceipt(payment);
    }

    Receipt toReceipt(Payment payment) {
        FeeInstallment installment = payment.getInstallment();
        return new Receipt(configService.getClientName(), payment.getReceiptNumber(), payment.getPaymentDate(),
                payment.getStudent().getFullName(), payment.getStudent().getAdmissionNumber(),
                installment.getStudentFee().getTitle(), installment.getLabel(), payment.getAmount(),
                payment.getPaymentMethod(), payment.getReferenceNumber(), installment.getPendingAmount(),
                configService.client().getCurrency());
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
