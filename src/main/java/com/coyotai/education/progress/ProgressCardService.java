package com.coyotai.education.progress;

import com.coyotai.education.academic.CourseService;
import com.coyotai.education.assessment.PublicationStatus;
import com.coyotai.education.audit.AuditService;
import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.common.Ref;
import com.coyotai.education.common.ResourceNotFoundException;
import com.coyotai.education.discipline.DisciplineRecordRepository;
import com.coyotai.education.discipline.StudentFineRepository;
import com.coyotai.education.notification.NotificationEvent;
import com.coyotai.education.notification.NotificationMessageFactory;
import com.coyotai.education.notification.NotificationService;
import com.coyotai.education.performance.PerformanceService;
import com.coyotai.education.performance.PerformanceService.StudentPerformance;
import com.coyotai.education.performance.PerformanceService.SubjectPerformance;
import com.coyotai.education.security.CurrentUser;
import com.coyotai.education.security.DataScope;
import com.coyotai.education.security.DataScopeService;
import com.coyotai.education.student.Student;
import com.coyotai.education.student.StudentRepository;
import com.coyotai.education.student.StudentService;
import com.coyotai.education.syllabus.SyllabusService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Progress cards: generated from the student's data for a period, reviewed, then published.
 * Students and parents only ever see published cards.
 */
@Service
public class ProgressCardService {

    private final ProgressCardRepository repository;
    private final CourseService courseService;
    private final StudentService studentService;
    private final StudentRepository studentRepository;
    private final PerformanceService performanceService;
    private final SyllabusService syllabusService;
    private final DisciplineRecordRepository disciplineRepository;
    private final StudentFineRepository fineRepository;
    private final DataScopeService dataScopeService;
    private final NotificationService notificationService;
    private final NotificationMessageFactory messageFactory;
    private final AuditService auditService;

    public ProgressCardService(ProgressCardRepository repository, CourseService courseService, StudentService studentService,
                               StudentRepository studentRepository, PerformanceService performanceService,
                               SyllabusService syllabusService, DisciplineRecordRepository disciplineRepository,
                               StudentFineRepository fineRepository, DataScopeService dataScopeService,
                               NotificationService notificationService, NotificationMessageFactory messageFactory,
                               AuditService auditService) {
        this.repository = repository;
        this.courseService = courseService;
        this.studentService = studentService;
        this.studentRepository = studentRepository;
        this.performanceService = performanceService;
        this.syllabusService = syllabusService;
        this.disciplineRepository = disciplineRepository;
        this.fineRepository = fineRepository;
        this.dataScopeService = dataScopeService;
        this.notificationService = notificationService;
        this.messageFactory = messageFactory;
        this.auditService = auditService;
    }

    public record GenerateRequest(
            Long studentId,
            Long batchId,
            @NotBlank(message = "Title is required") @Size(max = 150) String title,
            @NotNull(message = "Period start is required") LocalDate periodStart,
            @NotNull(message = "Period end is required") LocalDate periodEnd,
            @Size(max = 5000) String mentorRemarks
    ) {
    }

    public record UpdateRequest(
            @NotBlank(message = "Title is required") @Size(max = 150) String title,
            @Size(max = 5000) String mentorRemarks,
            List<ItemRemark> items
    ) {
        public record ItemRemark(Long itemId, @Size(max = 500) String remarks) {
        }
    }

    public record Item(Long id, Ref subject, String subjectName, BigDecimal testAverage, BigDecimal examAverage,
                       BigDecimal overallPercentage, String grade, String remarks) {
    }

    public record Card(Long id, Ref student, String admissionNumber, Ref batch, String title, LocalDate periodStart,
                       LocalDate periodEnd, BigDecimal attendancePercentage, BigDecimal dailyTestAverage,
                       BigDecimal weeklyTestAverage, BigDecimal examAverage, BigDecimal performanceScore,
                       String grade, BigDecimal syllabusCompletion, int disciplineCount, BigDecimal pendingFineAmount,
                       String mentorRemarks, PublicationStatus status, Instant publishedAt, List<Item> items) {

        public static Card from(ProgressCard c, boolean withItems) {
            List<Item> items = withItems ? c.getItems().stream()
                    .map(i -> new Item(i.getId(), i.getSubject() == null ? null : Ref.of(i.getSubject().getId(), i.getSubjectName()),
                            i.getSubjectName(), i.getTestAverage(), i.getExamAverage(), i.getOverallPercentage(),
                            i.getGrade(), i.getRemarks()))
                    .toList() : List.of();
            return new Card(c.getId(), Ref.of(c.getStudent().getId(), c.getStudent().getFullName()),
                    c.getStudent().getAdmissionNumber(),
                    c.getBatch() == null ? null : Ref.of(c.getBatch().getId(), c.getBatch().getName()),
                    c.getTitle(), c.getPeriodStart(), c.getPeriodEnd(), c.getAttendancePercentage(),
                    c.getDailyTestAverage(), c.getWeeklyTestAverage(), c.getExamAverage(), c.getPerformanceScore(),
                    c.getGrade(), c.getSyllabusCompletion(), c.getDisciplineCount(), c.getPendingFineAmount(),
                    c.getMentorRemarks(), c.getStatus(), c.getPublishedAt(), items);
        }
    }

    @Transactional(readOnly = true)
    public List<Card> search(Long studentId, Long batchId, PublicationStatus status) {
        DataScope scope = dataScopeService.current();
        if (batchId != null) {
            scope.requireBatch(batchId);
        }
        return repository.search(studentId, batchId, status, scope.isGlobal(), scope.batchIdsForQuery())
                .stream().map(card -> Card.from(card, false)).toList();
    }

    @Transactional(readOnly = true)
    public Card detail(Long id) {
        ProgressCard card = get(id);
        dataScopeService.requireStudent(card.getStudent());
        return Card.from(card, true);
    }

    /** Generates one card, or one per active student when a batch is given. */
    @Transactional
    public List<Card> generate(GenerateRequest request) {
        if (request.periodEnd().isBefore(request.periodStart())) {
            throw new BusinessRuleException("The period end cannot be before its start");
        }
        List<Student> students;
        if (request.studentId() != null) {
            Student student = studentService.getDetail(request.studentId());
            dataScopeService.requireStudent(student);
            students = List.of(student);
        } else if (request.batchId() != null) {
            dataScopeService.current().requireBatch(request.batchId());
            students = studentRepository.findActiveByBatchId(request.batchId()).stream()
                    .map(s -> studentService.getDetail(s.getId())).toList();
        } else {
            throw new BusinessRuleException("Choose a student or a batch");
        }
        List<Card> cards = new ArrayList<>();
        for (Student student : students) {
            cards.add(Card.from(build(student, request), true));
        }
        auditService.record("ProgressCard", cards.isEmpty() ? null : cards.get(0).id(), AuditService.CREATE,
                "Generated " + cards.size() + " progress card(s): " + request.title().trim());
        return cards;
    }

    private ProgressCard build(Student student, GenerateRequest request) {
        StudentPerformance performance = performanceService.compute(student, request.periodStart(), request.periodEnd());
        ProgressCard card = new ProgressCard();
        card.setStudent(student);
        card.setBatch(student.getBatch());
        card.setTitle(request.title().trim());
        card.setPeriodStart(request.periodStart());
        card.setPeriodEnd(request.periodEnd());
        card.setAttendancePercentage(decimal(performance.attendancePercentage()));
        card.setDailyTestAverage(decimal(performance.dailyTestAverage()));
        card.setWeeklyTestAverage(decimal(performance.weeklyTestAverage()));
        card.setExamAverage(decimal(performance.examAverage()));
        card.setPerformanceScore(decimal(performance.overall()));
        card.setGrade(performance.grade());
        card.setSyllabusCompletion(student.getBatch() == null ? null
                : decimal(syllabusService.progressFor(student.getBatch(), null).completion()));
        card.setDisciplineCount(disciplineRepository.findForStudent(student.getId(), request.periodStart(),
                request.periodEnd()).size());
        card.setPendingFineAmount(fineRepository.sumPendingForStudent(student.getId()));
        card.setMentorRemarks(request.mentorRemarks() == null || request.mentorRemarks().isBlank() ? null : request.mentorRemarks().trim());
        card.setStatus(PublicationStatus.DRAFT);

        int order = 0;
        for (SubjectPerformance subject : performance.subjects()) {
            ProgressCardItem item = new ProgressCardItem();
            item.setProgressCard(card);
            item.setSubject(courseService.getSubject(subject.subject().id()));
            item.setSubjectName(subject.subject().name());
            item.setTestAverage(decimal(subject.testAverage()));
            item.setExamAverage(decimal(subject.examAverage()));
            item.setOverallPercentage(decimal(subject.overall()));
            item.setGrade(subject.grade());
            item.setDisplayOrder(order++);
            card.getItems().add(item);
        }
        return repository.save(card);
    }

    @Transactional
    public Card update(Long id, UpdateRequest request) {
        ProgressCard card = get(id);
        dataScopeService.requireStudent(card.getStudent());
        if (card.getStatus() == PublicationStatus.PUBLISHED) {
            throw new BusinessRuleException("A published progress card cannot be edited. Move it back to review first.");
        }
        card.setTitle(request.title().trim());
        card.setMentorRemarks(request.mentorRemarks() == null || request.mentorRemarks().isBlank() ? null : request.mentorRemarks().trim());
        if (request.items() != null) {
            for (UpdateRequest.ItemRemark remark : request.items()) {
                card.getItems().stream().filter(item -> item.getId().equals(remark.itemId())).findFirst()
                        .ifPresent(item -> item.setRemarks(remark.remarks() == null || remark.remarks().isBlank() ? null : remark.remarks().trim()));
            }
        }
        auditService.record("ProgressCard", id, AuditService.UPDATE,
                "Updated progress card \"" + card.getTitle() + "\" for " + card.getStudent().getFullName());
        return Card.from(card, true);
    }

    @Transactional
    public Card changeStatus(Long id, PublicationStatus target) {
        ProgressCard card = get(id);
        dataScopeService.requireStudent(card.getStudent());
        if (!card.getStatus().canMoveTo(target)) {
            throw new BusinessRuleException("A progress card cannot move from " + card.getStatus() + " to " + target);
        }
        PublicationStatus previous = card.getStatus();
        card.setStatus(target);
        if (target == PublicationStatus.PUBLISHED) {
            card.setPublishedAt(Instant.now());
            card.setPublishedBy(CurrentUser.idOrNull());
            Student student = card.getStudent();
            String parentName = student.getParent() == null ? "Parent" : student.getParent().getName();
            String score = card.getPerformanceScore() == null ? "-" : card.getPerformanceScore().stripTrailingZeros().toPlainString() + "%";
            notificationService.publish(NotificationEvent.PROGRESS_CARD_PUBLISHED, student,
                    messageFactory.progressCard(parentName, student.getFullName(), card.getTitle(), score, card.getGrade()));
        } else {
            card.setPublishedAt(null);
            card.setPublishedBy(null);
        }
        auditService.record("ProgressCard", id, target == PublicationStatus.PUBLISHED ? AuditService.PUBLISH : AuditService.STATUS_CHANGE,
                "Progress card \"" + card.getTitle() + "\" for " + card.getStudent().getFullName() + ": " + previous + " -> " + target);
        return Card.from(card, true);
    }

    @Transactional(readOnly = true)
    public List<Card> publishedForStudent(Long studentId) {
        return repository.findPublishedForStudent(studentId).stream().map(card -> Card.from(card, false)).toList();
    }

    /** Detail of a published card, for the student it belongs to. */
    @Transactional(readOnly = true)
    public Card publishedDetail(Long id, Long studentId) {
        ProgressCard card = get(id);
        if (!card.getStudent().getId().equals(studentId) || card.getStatus() != PublicationStatus.PUBLISHED) {
            throw ResourceNotFoundException.of("Progress card", id);
        }
        return Card.from(card, true);
    }

    private ProgressCard get(Long id) {
        return repository.findDetail(id).orElseThrow(() -> ResourceNotFoundException.of("Progress card", id));
    }

    private BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
