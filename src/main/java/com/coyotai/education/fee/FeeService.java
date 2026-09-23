package com.coyotai.education.fee;

import com.coyotai.education.academic.AcademicYear;
import com.coyotai.education.academic.AcademicYearService;
import com.coyotai.education.academic.Batch;
import com.coyotai.education.academic.BatchService;
import com.coyotai.education.academic.Course;
import com.coyotai.education.academic.CourseService;
import com.coyotai.education.audit.AuditService;
import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.common.DuplicateResourceException;
import com.coyotai.education.common.Ref;
import com.coyotai.education.common.ResourceNotFoundException;
import com.coyotai.education.fee.FeeDtos.BulkPlanRequest;
import com.coyotai.education.fee.FeeDtos.BulkPlanResult;
import com.coyotai.education.fee.FeeDtos.DiscountRequest;
import com.coyotai.education.fee.FeeDtos.FeeSummary;
import com.coyotai.education.fee.FeeDtos.InstallmentResponse;
import com.coyotai.education.fee.FeeDtos.InstallmentUpdateRequest;
import com.coyotai.education.fee.FeeDtos.PaymentResponse;
import com.coyotai.education.fee.FeeDtos.PlanRequest;
import com.coyotai.education.fee.FeeDtos.PlanResponse;
import com.coyotai.education.fee.FeeDtos.PlanUpdateRequest;
import com.coyotai.education.fee.FeeDtos.StudentDiscount;
import com.coyotai.education.notification.NotificationEvent;
import com.coyotai.education.notification.NotificationMessageFactory;
import com.coyotai.education.notification.NotificationService;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.student.Student;
import com.coyotai.education.student.StudentRepository;
import com.coyotai.education.student.StudentService;
import com.coyotai.education.util.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fee plans, installments, overdue tracking and fee reminders.
 *
 * <p>The fee of a plan always comes from the course master ({@code Course.feeAmount}); what
 * differs between students is only their discount, recorded with a reason. A plan keeps the
 * course fee it was created with, so changing a course fee affects new plans only.
 */
@Service
public class FeeService {

    private static final Logger log = LoggerFactory.getLogger(FeeService.class);

    private final StudentFeeRepository planRepository;
    private final FeeInstallmentRepository installmentRepository;
    private final PaymentRepository paymentRepository;
    private final StudentRepository studentRepository;
    private final StudentService studentService;
    private final CourseService courseService;
    private final AcademicYearService academicYearService;
    private final BatchService batchService;
    private final NotificationService notificationService;
    private final NotificationMessageFactory messageFactory;
    private final ProjectConfigService configService;
    private final AuditService auditService;

    public FeeService(StudentFeeRepository planRepository, FeeInstallmentRepository installmentRepository,
                      PaymentRepository paymentRepository, StudentRepository studentRepository,
                      StudentService studentService, CourseService courseService,
                      AcademicYearService academicYearService, BatchService batchService,
                      NotificationService notificationService, NotificationMessageFactory messageFactory,
                      ProjectConfigService configService, AuditService auditService) {
        this.planRepository = planRepository;
        this.installmentRepository = installmentRepository;
        this.paymentRepository = paymentRepository;
        this.studentRepository = studentRepository;
        this.studentService = studentService;
        this.courseService = courseService;
        this.academicYearService = academicYearService;
        this.batchService = batchService;
        this.notificationService = notificationService;
        this.messageFactory = messageFactory;
        this.configService = configService;
        this.auditService = auditService;
    }

    /** What varies per student when a plan is created. */
    private record PlanTerms(BigDecimal discount, String reason, Integer installmentCount, LocalDate firstDueDate,
                             String title, String notes) {
    }

    // ---- Plans ----------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PlanResponse> plans(Long studentId) {
        return planRepository.search(studentId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse plan(Long id) {
        return toResponse(getPlan(id));
    }

    @Transactional
    public PlanResponse createPlan(PlanRequest request) {
        Student student = studentService.getDetail(request.studentId());
        Course course = request.courseId() != null ? courseService.getCourse(request.courseId()) : student.getCourse();
        AcademicYear year = request.academicYearId() != null
                ? academicYearService.get(request.academicYearId()) : student.getAcademicYear();
        StudentFee plan = buildPlan(student, course, year, new PlanTerms(request.discountAmount(),
                request.discountReason(), request.installmentCount(), request.firstDueDate(), request.title(),
                request.notes()));
        auditService.record("StudentFee", plan.getId(), AuditService.CREATE, "Fee plan \"" + plan.getTitle()
                + "\" for " + student.getFullName() + ": course fee " + plan.getTotalAmount().toPlainString()
                + discountText(plan) + ", " + plan.getInstallments().size() + " installments");
        return toResponse(plan);
    }

    /**
     * A plan at the batch's course fee for every active student of the batch, each with their
     * own discount. Students who already have an active plan for the course and year are skipped.
     */
    @Transactional
    public BulkPlanResult createPlansForBatch(BulkPlanRequest request) {
        Batch batch = batchService.getBatch(request.batchId());
        List<Student> students = studentRepository.findActiveByBatchId(batch.getId());

        Map<Long, StudentDiscount> discounts = new HashMap<>();
        if (request.discounts() != null) {
            for (StudentDiscount discount : request.discounts()) {
                boolean inBatch = students.stream().anyMatch(student -> student.getId().equals(discount.studentId()));
                if (!inBatch) {
                    throw new BusinessRuleException("Student " + discount.studentId() + " is not an active student of "
                            + batch.getName());
                }
                discounts.put(discount.studentId(), discount);
            }
        }

        requireCourseFee(batch.getCourse());
        List<PlanResponse> created = new ArrayList<>();
        int skipped = 0;
        for (Student student : students) {
            if (hasActivePlan(student, batch.getCourse(), batch.getAcademicYear())) {
                skipped++;
                continue;
            }
            StudentDiscount discount = discounts.get(student.getId());
            StudentFee plan = buildPlan(student, batch.getCourse(), batch.getAcademicYear(), new PlanTerms(
                    discount == null ? null : discount.discountAmount(), discount == null ? null : discount.reason(),
                    request.installmentCount(), request.firstDueDate(), request.title(), request.notes()));
            created.add(toResponse(plan));
        }
        BigDecimal totalDiscount = created.stream().map(PlanResponse::discountAmount).reduce(Money.ZERO, Money::add);
        auditService.record("StudentFee", batch.getId(), AuditService.CREATE, "Created " + created.size()
                + " fee plans for batch " + batch.getName() + " at the course fee of "
                + batch.getCourse().getFeeAmount().toPlainString() + " (discounts " + totalDiscount.toPlainString()
                + ", " + skipped + " already had a plan)");
        return new BulkPlanResult(created, skipped);
    }

    private StudentFee buildPlan(Student student, Course course, AcademicYear year, PlanTerms terms) {
        if (course == null) {
            throw new BusinessRuleException(student.getFullName() + " is not on a course yet - choose the course for the fee plan");
        }
        BigDecimal fee = requireCourseFee(course);
        if (hasActivePlan(student, course, year)) {
            throw new DuplicateResourceException(student.getFullName() + " already has an active fee plan for "
                    + course.getName() + (year == null ? "" : " " + year.getName()));
        }
        BigDecimal discount = Money.scale(terms.discount());
        if (discount.compareTo(fee) > 0) {
            throw new BusinessRuleException("The discount for " + student.getFullName() + " (" + discount.toPlainString()
                    + ") is more than the course fee of " + fee.toPlainString());
        }
        BigDecimal net = fee.subtract(discount);
        int count = terms.installmentCount() != null ? terms.installmentCount()
                : course.getDefaultInstallments() != null ? course.getDefaultInstallments() : 1;

        StudentFee plan = new StudentFee();
        plan.setStudent(student);
        plan.setCourse(course);
        plan.setAcademicYear(year);
        plan.setTitle(isBlank(terms.title()) ? course.getName() + (year == null ? "" : " " + year.getName())
                : terms.title().trim());
        plan.setTotalAmount(fee);
        plan.setDiscountAmount(discount);
        plan.setDiscountReason(discount.signum() > 0 ? trimToNull(terms.reason()) : null);
        plan.setNetAmount(net);
        plan.setStatus(StudentFee.Status.ACTIVE);
        plan.setNotes(trimToNull(terms.notes()));
        planRepository.save(plan);

        // A full scholarship leaves nothing to collect, so there are no installments.
        if (net.signum() > 0) {
            LocalDate today = configService.today();
            List<BigDecimal> amounts = FeeCalculator.split(net, count);
            for (int i = 0; i < amounts.size(); i++) {
                FeeInstallment installment = new FeeInstallment();
                installment.setStudentFee(plan);
                installment.setStudent(student);
                installment.setInstallmentNo(i + 1);
                installment.setLabel("Installment " + (i + 1));
                installment.setDueDate(terms.firstDueDate().plusMonths(i));
                installment.setAmount(amounts.get(i));
                installment.setPaidAmount(Money.ZERO);
                FeeCalculator.recalculate(installment, today);
                installmentRepository.save(installment);
                plan.getInstallments().add(installment);
            }
        }
        return plan;
    }

    /**
     * Gives a student a different discount on an existing plan. The course fee stays as it was;
     * the new balance is divided equally over the installments not yet paid, without touching
     * paid or waived installments or anything already paid.
     */
    @Transactional
    public PlanResponse changeDiscount(Long id, DiscountRequest request) {
        StudentFee plan = getPlan(id);
        if (plan.getStatus() != StudentFee.Status.ACTIVE) {
            throw new BusinessRuleException("Only an active fee plan can get a new discount");
        }
        BigDecimal fee = plan.getTotalAmount();
        BigDecimal discount = Money.scale(request.discountAmount());
        if (discount.compareTo(fee) > 0) {
            throw new BusinessRuleException("The discount cannot be more than the course fee of " + fee.toPlainString());
        }
        BigDecimal minimumNet = FeeCalculator.minimumNet(plan.getInstallments());
        BigDecimal newNet = fee.subtract(discount);
        if (newNet.compareTo(minimumNet) < 0) {
            throw new BusinessRuleException(plan.getStudent().getFullName() + " has already paid "
                    + minimumNet.toPlainString() + " on this plan, so the discount can be at most "
                    + fee.subtract(minimumNet).toPlainString());
        }

        BigDecimal previousDiscount = plan.getDiscountAmount();
        List<FeeInstallment> nothingLeft = FeeCalculator.redistribute(plan.getInstallments(), newNet, configService.today());
        for (FeeInstallment installment : nothingLeft) {
            // Unpaid and now worth nothing: the discount covered it completely.
            plan.getInstallments().remove(installment);
            installmentRepository.delete(installment);
        }
        plan.setDiscountAmount(discount);
        plan.setDiscountReason(discount.signum() > 0 ? trimToNull(request.reason()) : null);
        plan.setNetAmount(newNet);

        auditService.record("StudentFee", id, AuditService.UPDATE, "Discount for " + plan.getStudent().getFullName()
                + " on \"" + plan.getTitle() + "\" changed from " + previousDiscount.toPlainString() + " to "
                + discount.toPlainString() + (plan.getDiscountReason() == null ? "" : " (" + plan.getDiscountReason() + ")")
                + "; fee payable now " + newNet.toPlainString());
        return toResponse(plan);
    }

    @Transactional
    public PlanResponse updatePlan(Long id, PlanUpdateRequest request) {
        StudentFee plan = getPlan(id);
        plan.setTitle(request.title().trim());
        if (request.status() != null) {
            plan.setStatus(request.status());
        }
        plan.setNotes(trimToNull(request.notes()));
        auditService.record("StudentFee", id, AuditService.UPDATE, "Updated fee plan \"" + plan.getTitle() + "\"");
        return toResponse(plan);
    }

    private BigDecimal requireCourseFee(Course course) {
        if (course.getFeeAmount() == null) {
            throw new BusinessRuleException("Set the fee for " + course.getName()
                    + " on the Courses screen before creating fee plans");
        }
        return Money.scale(course.getFeeAmount());
    }

    private boolean hasActivePlan(Student student, Course course, AcademicYear year) {
        return planRepository.existsForCourse(student.getId(), course.getId(), year == null ? null : year.getId(),
                StudentFee.Status.ACTIVE);
    }

    private String discountText(StudentFee plan) {
        if (plan.getDiscountAmount().signum() == 0) {
            return "";
        }
        return ", discount " + plan.getDiscountAmount().toPlainString()
                + (plan.getDiscountReason() == null ? "" : " (" + plan.getDiscountReason() + ")")
                + ", payable " + plan.getNetAmount().toPlainString();
    }

    // ---- Installments -----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<InstallmentResponse> installments(Long studentId, Long batchId, InstallmentStatus status,
                                                  LocalDate from, LocalDate to) {
        LocalDate start = from == null ? configService.today().minusYears(2) : from;
        LocalDate end = to == null ? configService.today().plusYears(2) : to;
        return installmentRepository.search(studentId, batchId, status, start, end).stream()
                .map(InstallmentResponse::from).toList();
    }

    /** Label and due date only: amounts follow from the course fee and the student's discount. */
    @Transactional
    public InstallmentResponse updateInstallment(Long id, InstallmentUpdateRequest request) {
        FeeInstallment installment = getInstallment(id);
        installment.setLabel(request.label().trim());
        installment.setDueDate(request.dueDate());
        FeeCalculator.recalculate(installment, configService.today());
        auditService.record("FeeInstallment", id, AuditService.UPDATE,
                "Installment \"" + installment.getLabel() + "\" of " + installment.getStudent().getFullName()
                        + " now due " + request.dueDate());
        return InstallmentResponse.from(installment);
    }

    @Transactional
    public InstallmentResponse waive(Long id) {
        FeeInstallment installment = getInstallment(id);
        if (installment.getStatus() == InstallmentStatus.PAID) {
            throw new BusinessRuleException("A paid installment cannot be waived");
        }
        BigDecimal waived = installment.getPendingAmount();
        installment.setStatus(InstallmentStatus.WAIVED);
        FeeCalculator.recalculate(installment, configService.today());
        auditService.record("FeeInstallment", id, AuditService.STATUS_CHANGE,
                "Waived " + waived.toPlainString() + " on \"" + installment.getLabel() + "\" for "
                        + installment.getStudent().getFullName());
        return InstallmentResponse.from(installment);
    }

    /** Called by the payment flow inside the same transaction. */
    @Transactional
    public FeeInstallment applyPayment(FeeInstallment installment, BigDecimal amount) {
        installment.setPaidAmount(Money.add(installment.getPaidAmount(), amount));
        FeeCalculator.recalculate(installment, configService.today());
        return installmentRepository.save(installment);
    }

    /** Flags installments whose due date has passed. Run daily before reminders. */
    @Transactional
    public int refreshOverdue() {
        LocalDate today = configService.today();
        List<FeeInstallment> overdue = installmentRepository.findNewlyOverdue(today);
        overdue.forEach(installment -> FeeCalculator.recalculate(installment, today));
        return overdue.size();
    }

    // ---- Reminders --------------------------------------------------------------

    /**
     * Queues a fee reminder. Returns false when nothing was sent: the student is inactive, the
     * parent cannot be messaged, or a reminder already went out today.
     */
    @Transactional
    public boolean queueReminder(FeeInstallment installment, boolean manual) {
        Student student = installment.getStudent();
        if (!student.isActive()) {
            return false;
        }
        if (!notificationService.canMessageParent(student.getParent()) && student.getUser() == null) {
            return false;
        }
        LocalDate today = configService.today();
        if (today.equals(installment.getLastReminderDate())) {
            return false;
        }
        Instant startOfToday = today.atStartOfDay(configService.zoneId()).toInstant();
        if (notificationService.hasEventSince(student.getId(), NotificationEvent.FEE_DUE, startOfToday)) {
            return false;
        }
        String parentName = student.getParent() == null ? "Parent" : student.getParent().getName();
        boolean queued = notificationService.publish(NotificationEvent.FEE_DUE, student,
                messageFactory.feeDue(parentName, student.getFullName(), installment.getLabel(),
                        installment.getPendingAmount(), installment.getDueDate())).anyQueued();
        if (queued) {
            installment.setReminderCount(installment.getReminderCount() + 1);
            installment.setLastReminderDate(today);
            log.info("Fee reminder queued for installment {} ({})", installment.getId(), manual ? "manual" : "automatic");
        }
        return queued;
    }

    @Transactional
    public void sendManualReminder(Long installmentId) {
        FeeInstallment installment = getInstallment(installmentId);
        if (installment.getStatus() == InstallmentStatus.PAID || installment.getStatus() == InstallmentStatus.WAIVED) {
            throw new BusinessRuleException("This installment has nothing outstanding");
        }
        if (!queueReminder(installment, true)) {
            throw new BusinessRuleException("No reminder was queued: the parent may have opted out or have no "
                    + "WhatsApp number, or a reminder was already sent today.");
        }
        auditService.record("FeeInstallment", installmentId, AuditService.UPDATE,
                "Sent a manual fee reminder to " + installment.getStudent().getFullName());
    }

    // ---- Student summary ---------------------------------------------------------

    /** Everything about one student's fees; the student portal shows exactly this. */
    @Transactional(readOnly = true)
    public FeeSummary summary(Long studentId) {
        List<StudentFee> plans = planRepository.search(studentId).stream()
                .filter(plan -> plan.getStatus() != StudentFee.Status.CANCELLED).toList();
        List<FeeInstallment> installments = plans.stream().flatMap(plan -> plan.getInstallments().stream()).toList();

        BigDecimal total = sum(plans.stream().map(StudentFee::getTotalAmount).toList());
        BigDecimal discount = sum(plans.stream().map(StudentFee::getDiscountAmount).toList());
        BigDecimal net = sum(plans.stream().map(StudentFee::getNetAmount).toList());
        BigDecimal paid = sum(installments.stream().map(FeeInstallment::getPaidAmount).toList());
        BigDecimal outstanding = sum(installments.stream().filter(FeeCalculator::isOpen).map(FeeInstallment::getPendingAmount).toList());
        BigDecimal overdue = sum(installments.stream().filter(i -> i.getStatus() == InstallmentStatus.OVERDUE)
                .map(FeeInstallment::getPendingAmount).toList());
        FeeInstallment next = installments.stream().filter(FeeCalculator::isOpen)
                .min(Comparator.comparing(FeeInstallment::getDueDate)).orElse(null);

        List<PaymentResponse> payments = paymentRepository.findForStudent(studentId).stream()
                .map(PaymentResponse::from).toList();
        return new FeeSummary(total, discount, net, paid, outstanding, overdue,
                next == null ? null : next.getPendingAmount(), next == null ? null : next.getDueDate(),
                plans.stream().map(this::toResponse).toList(), payments);
    }

    private PlanResponse toResponse(StudentFee plan) {
        List<InstallmentResponse> installments = plan.getInstallments().stream()
                .sorted(Comparator.comparingInt(FeeInstallment::getInstallmentNo))
                .map(InstallmentResponse::from).toList();
        BigDecimal paid = sum(plan.getInstallments().stream().map(FeeInstallment::getPaidAmount).toList());
        BigDecimal outstanding = sum(plan.getInstallments().stream().filter(FeeCalculator::isOpen)
                .map(FeeInstallment::getPendingAmount).toList());
        Student student = plan.getStudent();
        return new PlanResponse(plan.getId(), Ref.of(student.getId(), student.getFullName()), student.getAdmissionNumber(),
                plan.getTitle(), plan.getCourse() == null ? null : Ref.of(plan.getCourse().getId(), plan.getCourse().getName()),
                plan.getAcademicYear() == null ? null : Ref.of(plan.getAcademicYear().getId(), plan.getAcademicYear().getName()),
                plan.getTotalAmount(), plan.getDiscountAmount(), plan.getDiscountReason(), plan.getNetAmount(), paid,
                outstanding, plan.getStatus(), plan.getNotes(), installments);
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return Money.scale(values.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    public StudentFee getPlan(Long id) {
        return planRepository.findDetail(id).orElseThrow(() -> ResourceNotFoundException.of("Fee plan", id));
    }

    public FeeInstallment getInstallment(Long id) {
        return installmentRepository.findDetail(id).orElseThrow(() -> ResourceNotFoundException.of("Installment", id));
    }
}
