package com.coyotai.education.assessment;

import com.coyotai.education.academic.Batch;
import com.coyotai.education.academic.BatchService;
import com.coyotai.education.academic.CourseService;
import com.coyotai.education.academic.Subject;
import com.coyotai.education.assessment.AssessmentDtos.ExamRequest;
import com.coyotai.education.assessment.AssessmentDtos.ExamResponse;
import com.coyotai.education.assessment.AssessmentDtos.MarksEntryRequest;
import com.coyotai.education.assessment.AssessmentDtos.MarksRow;
import com.coyotai.education.assessment.AssessmentDtos.MarksSheet;
import com.coyotai.education.assessment.AssessmentDtos.TestRequest;
import com.coyotai.education.assessment.AssessmentDtos.TestResponse;
import com.coyotai.education.audit.AuditService;
import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.common.ForbiddenException;
import com.coyotai.education.common.Ref;
import com.coyotai.education.common.ResourceNotFoundException;
import com.coyotai.education.notification.NotificationEvent;
import com.coyotai.education.notification.NotificationMessageFactory;
import com.coyotai.education.notification.NotificationService;
import com.coyotai.education.performance.GradeScale;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.security.AppUserDetails;
import com.coyotai.education.security.CurrentUser;
import com.coyotai.education.security.DataScope;
import com.coyotai.education.security.DataScopeService;
import com.coyotai.education.staff.StaffService;
import com.coyotai.education.student.Student;
import com.coyotai.education.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Daily/weekly tests and exams, marks entry and the publication workflow.
 * Marks are editable until PUBLISHED; students never see anything before that.
 */
@Service
public class AssessmentService {

    private final AcademicTestRepository testRepository;
    private final AcademicTestResultRepository testResultRepository;
    private final ExamRepository examRepository;
    private final ExamResultRepository examResultRepository;
    private final ExamTypeRepository examTypeRepository;
    private final StudentRepository studentRepository;
    private final BatchService batchService;
    private final CourseService courseService;
    private final StaffService staffService;
    private final DataScopeService dataScopeService;
    private final GradeScale gradeScale;
    private final NotificationService notificationService;
    private final NotificationMessageFactory messageFactory;
    private final ProjectConfigService configService;
    private final AuditService auditService;

    public AssessmentService(AcademicTestRepository testRepository, AcademicTestResultRepository testResultRepository,
                             ExamRepository examRepository, ExamResultRepository examResultRepository,
                             ExamTypeRepository examTypeRepository, StudentRepository studentRepository,
                             BatchService batchService, CourseService courseService, StaffService staffService,
                             DataScopeService dataScopeService, GradeScale gradeScale,
                             NotificationService notificationService, NotificationMessageFactory messageFactory,
                             ProjectConfigService configService, AuditService auditService) {
        this.testRepository = testRepository;
        this.testResultRepository = testResultRepository;
        this.examRepository = examRepository;
        this.examResultRepository = examResultRepository;
        this.examTypeRepository = examTypeRepository;
        this.studentRepository = studentRepository;
        this.batchService = batchService;
        this.courseService = courseService;
        this.staffService = staffService;
        this.dataScopeService = dataScopeService;
        this.gradeScale = gradeScale;
        this.notificationService = notificationService;
        this.messageFactory = messageFactory;
        this.configService = configService;
        this.auditService = auditService;
    }

    // =========================================================================
    // Tests
    // =========================================================================

    @Transactional(readOnly = true)
    public List<TestResponse> tests(AcademicTest.Type type, Long batchId, Long subjectId, PublicationStatus status,
                                    LocalDate from, LocalDate to) {
        DataScope scope = dataScopeService.current();
        if (batchId != null) {
            scope.requireBatch(batchId);
        }
        List<AcademicTest> tests = testRepository.search(type, batchId, subjectId, status, rangeFrom(from), rangeTo(to),
                scope.isGlobal(), scope.batchIdsForQuery());
        Map<Long, Long> counts = counts(testResultRepository.countByTests(ids(tests, AcademicTest::getId)));
        return tests.stream().map(test -> TestResponse.from(test, counts.getOrDefault(test.getId(), 0L))).toList();
    }

    @Transactional
    public TestResponse createTest(TestRequest request) {
        Batch batch = batchService.getBatch(request.batchId());
        dataScopeService.current().requireBatch(batch.getId());
        AcademicTest test = new AcademicTest();
        applyTest(test, request, batch);
        testRepository.save(test);
        auditService.record("Test", test.getId(), AuditService.CREATE,
                "Created " + test.getTestType().name().toLowerCase() + " test " + test.getTitle() + " for " + batch.getName());
        return TestResponse.from(test, 0);
    }

    @Transactional
    public TestResponse updateTest(Long id, TestRequest request) {
        AcademicTest test = getTest(id);
        dataScopeService.current().requireBatch(test.getBatch().getId());
        if (!test.getStatus().marksEditable()) {
            throw new BusinessRuleException("A published test cannot be changed. Move it back to review first.");
        }
        Batch batch = batchService.getBatch(request.batchId());
        dataScopeService.current().requireBatch(batch.getId());
        applyTest(test, request, batch);
        auditService.record("Test", id, AuditService.UPDATE, "Updated test " + test.getTitle());
        return TestResponse.from(test, testResultRepository.findByTest(id).size());
    }

    private void applyTest(AcademicTest test, TestRequest request, Batch batch) {
        Subject subject = subjectOf(batch, request.subjectId());
        test.setTestType(request.testType());
        test.setTitle(request.title().trim());
        test.setBatch(batch);
        test.setSubject(subject);
        test.setTestDate(request.testDate());
        test.setWeekNumber(request.testType() == AcademicTest.Type.WEEKLY
                ? (request.weekNumber() != null ? request.weekNumber() : request.testDate().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
                : null);
        test.setMaxMarks(request.maxMarks());
        test.setRemarks(blankToNull(request.remarks()));
        if (test.getMaxMarks() != null && test.getId() != null) {
            // Lowering the maximum must not leave existing marks above it.
            testResultRepository.findByTest(test.getId()).stream()
                    .filter(result -> result.getMarksObtained() != null && result.getMarksObtained().compareTo(request.maxMarks()) > 0)
                    .findFirst()
                    .ifPresent(result -> {
                        throw new BusinessRuleException("Existing marks exceed the new maximum for " + result.getStudent().getFullName());
                    });
        }
    }

    @Transactional(readOnly = true)
    public MarksSheet testSheet(Long id) {
        AcademicTest test = getTest(id);
        dataScopeService.current().requireBatchSubject(test.getBatch().getId(), test.getSubject().getId());
        Map<Long, AcademicTestResult> results = testResultRepository.findByTest(id).stream()
                .collect(Collectors.toMap(result -> result.getStudent().getId(), Function.identity()));
        List<MarksRow> rows = studentRepository.findActiveByBatchId(test.getBatch().getId()).stream()
                .map(student -> {
                    AcademicTestResult result = results.get(student.getId());
                    BigDecimal marks = result == null ? null : result.getMarksObtained();
                    return new MarksRow(student.getId(), student.getAdmissionNumber(), student.getFullName(), marks,
                            result != null && result.isAbsent(),
                            gradeScale.gradeFor(MarksValidator.percentage(marks, test.getMaxMarks())),
                            result == null ? null : result.getRemarks());
                })
                .toList();
        return new MarksSheet(test.getId(), test.getTitle(), Ref.of(test.getBatch().getId(), test.getBatch().getName()),
                Ref.of(test.getSubject().getId(), test.getSubject().getName()), test.getTestDate(), test.getMaxMarks(),
                null, test.getStatus(), test.getStatus().marksEditable(), rows);
    }

    @Transactional
    public MarksSheet saveTestMarks(Long id, MarksEntryRequest request) {
        AcademicTest test = getTest(id);
        dataScopeService.current().requireBatchSubject(test.getBatch().getId(), test.getSubject().getId());
        if (!test.getStatus().marksEditable()) {
            throw new BusinessRuleException("Marks of a published test are locked. Move it back to review to correct them.");
        }
        Map<Long, Student> students = activeStudents(test.getBatch().getId());
        Map<Long, AcademicTestResult> existing = testResultRepository.findByTest(id).stream()
                .collect(Collectors.toMap(result -> result.getStudent().getId(), Function.identity()));
        int changed = 0;
        for (MarksEntryRequest.Entry entry : request.entries()) {
            Student student = requireInBatch(students, entry.studentId(), test.getBatch().getName());
            BigDecimal marks = MarksValidator.validate(entry.marksObtained(), entry.absent(), test.getMaxMarks(), student.getFullName());
            AcademicTestResult result = existing.get(student.getId());
            if (result == null) {
                result = new AcademicTestResult();
                result.setTest(test);
                result.setStudent(student);
            }
            if (result.getId() == null || !sameMarks(result.getMarksObtained(), marks) || result.isAbsent() != entry.absent()) {
                changed++;
            }
            result.setMarksObtained(marks);
            result.setAbsent(entry.absent());
            result.setRemarks(blankToNull(entry.remarks()));
            testResultRepository.save(result);
        }
        auditService.record("Test", id, AuditService.UPDATE,
                "Entered marks for " + test.getTitle() + " (" + changed + " changed)");
        return testSheet(id);
    }

    @Transactional
    public TestResponse changeTestStatus(Long id, PublicationStatus target) {
        AcademicTest test = getTest(id);
        dataScopeService.current().requireBatch(test.getBatch().getId());
        transition(test.getStatus(), target, "test");
        if (target == PublicationStatus.PUBLISHED && testResultRepository.findByTest(id).isEmpty()) {
            throw new BusinessRuleException("Enter marks before publishing the test");
        }
        PublicationStatus previous = test.getStatus();
        test.setStatus(target);
        stampPublication(target, test::setPublishedAt, test::setPublishedBy);
        auditService.record("Test", id, target == PublicationStatus.PUBLISHED ? AuditService.PUBLISH : AuditService.STATUS_CHANGE,
                "Test " + test.getTitle() + ": " + previous + " -> " + target);
        return TestResponse.from(test, testResultRepository.findByTest(id).size());
    }

    // =========================================================================
    // Exams
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ExamResponse> exams(Long batchId, Long subjectId, PublicationStatus status, LocalDate from, LocalDate to) {
        DataScope scope = dataScopeService.current();
        if (batchId != null) {
            scope.requireBatch(batchId);
        }
        List<Exam> exams = examRepository.search(batchId, subjectId, status, rangeFrom(from), rangeTo(to),
                scope.isGlobal(), scope.batchIdsForQuery());
        Map<Long, Long> counts = counts(examResultRepository.countByExams(ids(exams, Exam::getId)));
        return exams.stream().map(exam -> ExamResponse.from(exam, counts.getOrDefault(exam.getId(), 0L))).toList();
    }

    @Transactional
    public ExamResponse createExam(ExamRequest request) {
        Exam exam = new Exam();
        applyExam(exam, request);
        examRepository.save(exam);
        auditService.record("Exam", exam.getId(), AuditService.CREATE,
                "Created exam " + exam.getName() + " for " + exam.getBatch().getName());
        return ExamResponse.from(exam, 0);
    }

    @Transactional
    public ExamResponse updateExam(Long id, ExamRequest request) {
        Exam exam = getExam(id);
        if (!exam.getStatus().marksEditable()) {
            throw new BusinessRuleException("A published exam cannot be changed. Move it back to review first.");
        }
        applyExam(exam, request);
        auditService.record("Exam", id, AuditService.UPDATE, "Updated exam " + exam.getName());
        return ExamResponse.from(exam, examResultRepository.findByExam(id).size());
    }

    private void applyExam(Exam exam, ExamRequest request) {
        if (request.passingMarks().compareTo(request.maxMarks()) > 0) {
            throw new BusinessRuleException("Passing marks cannot be more than the maximum marks");
        }
        if (request.startTime() != null && request.endTime() != null && !request.startTime().isBefore(request.endTime())) {
            throw new BusinessRuleException("The start time must be before the end time");
        }
        Batch batch = batchService.getBatch(request.batchId());
        dataScopeService.current().requireBatch(batch.getId());
        exam.setName(request.name().trim());
        exam.setExamType(examTypeRepository.findById(request.examTypeId())
                .orElseThrow(() -> ResourceNotFoundException.of("Exam type", request.examTypeId())));
        exam.setBatch(batch);
        exam.setCourse(batch.getCourse());
        exam.setSubject(subjectOf(batch, request.subjectId()));
        exam.setExamDate(request.examDate());
        exam.setStartTime(request.startTime());
        exam.setEndTime(request.endTime());
        exam.setMaxMarks(request.maxMarks());
        exam.setPassingMarks(request.passingMarks());
        exam.setFaculty(request.facultyId() == null ? null : staffService.getFaculty(request.facultyId()));
        exam.setRemarks(blankToNull(request.remarks()));
    }

    @Transactional(readOnly = true)
    public MarksSheet examSheet(Long id) {
        Exam exam = getExam(id);
        requireExamAccess(exam);
        Map<Long, ExamResult> results = examResultRepository.findByExam(id).stream()
                .collect(Collectors.toMap(result -> result.getStudent().getId(), Function.identity()));
        List<MarksRow> rows = studentRepository.findActiveByBatchId(exam.getBatch().getId()).stream()
                .map(student -> {
                    ExamResult result = results.get(student.getId());
                    return new MarksRow(student.getId(), student.getAdmissionNumber(), student.getFullName(),
                            result == null ? null : result.getMarksObtained(), result != null && result.isAbsent(),
                            result == null ? null : result.getGrade(), result == null ? null : result.getRemarks());
                })
                .toList();
        return new MarksSheet(exam.getId(), exam.getName(), Ref.of(exam.getBatch().getId(), exam.getBatch().getName()),
                Ref.of(exam.getSubject().getId(), exam.getSubject().getName()), exam.getExamDate(), exam.getMaxMarks(),
                exam.getPassingMarks(), exam.getStatus(), exam.getStatus().marksEditable(), rows);
    }

    @Transactional
    public MarksSheet saveExamMarks(Long id, MarksEntryRequest request) {
        Exam exam = getExam(id);
        requireExamAccess(exam);
        if (!exam.getStatus().marksEditable()) {
            throw new BusinessRuleException("Marks of a published exam are locked. Move it back to review to correct them.");
        }
        Map<Long, Student> students = activeStudents(exam.getBatch().getId());
        Map<Long, ExamResult> existing = examResultRepository.findByExam(id).stream()
                .collect(Collectors.toMap(result -> result.getStudent().getId(), Function.identity()));
        int changed = 0;
        for (MarksEntryRequest.Entry entry : request.entries()) {
            Student student = requireInBatch(students, entry.studentId(), exam.getBatch().getName());
            BigDecimal marks = MarksValidator.validate(entry.marksObtained(), entry.absent(), exam.getMaxMarks(), student.getFullName());
            ExamResult result = existing.get(student.getId());
            if (result == null) {
                result = new ExamResult();
                result.setExam(exam);
                result.setStudent(student);
            }
            if (result.getId() == null || !sameMarks(result.getMarksObtained(), marks) || result.isAbsent() != entry.absent()) {
                changed++;
            }
            result.setMarksObtained(marks);
            result.setAbsent(entry.absent());
            result.setGrade(entry.absent() ? null : gradeScale.gradeFor(MarksValidator.percentage(marks, exam.getMaxMarks())));
            result.setRemarks(blankToNull(entry.remarks()));
            examResultRepository.save(result);
        }
        auditService.record("Exam", id, AuditService.UPDATE, "Entered marks for " + exam.getName() + " (" + changed + " changed)");
        return examSheet(id);
    }

    @Transactional
    public ExamResponse changeExamStatus(Long id, PublicationStatus target) {
        Exam exam = getExam(id);
        dataScopeService.current().requireBatch(exam.getBatch().getId());
        transition(exam.getStatus(), target, "exam");
        List<ExamResult> results = examResultRepository.findByExam(id);
        if (target == PublicationStatus.PUBLISHED && results.isEmpty()) {
            throw new BusinessRuleException("Enter marks before publishing the exam");
        }
        PublicationStatus previous = exam.getStatus();
        exam.setStatus(target);
        stampPublication(target, exam::setPublishedAt, exam::setPublishedBy);
        if (target == PublicationStatus.PUBLISHED) {
            for (ExamResult result : results) {
                Student student = result.getStudent();
                String parentName = student.getParent() == null ? "Parent" : student.getParent().getName();
                String marks = result.isAbsent() ? "Absent"
                        : result.getMarksObtained().stripTrailingZeros().toPlainString() + "/" + exam.getMaxMarks().stripTrailingZeros().toPlainString();
                notificationService.publish(NotificationEvent.EXAM_RESULT_PUBLISHED, student,
                        messageFactory.examResult(parentName, student.getFullName(), exam.getName(),
                                exam.getSubject().getName(), marks, result.getGrade()));
            }
        }
        auditService.record("Exam", id, target == PublicationStatus.PUBLISHED ? AuditService.PUBLISH : AuditService.STATUS_CHANGE,
                "Exam " + exam.getName() + ": " + previous + " -> " + target);
        return ExamResponse.from(exam, results.size());
    }

    /** Faculty may enter marks for exams they own or batch/subjects they teach. */
    private void requireExamAccess(Exam exam) {
        DataScope scope = dataScopeService.current();
        AppUserDetails user = CurrentUser.require();
        boolean ownExam = exam.getFaculty() != null && exam.getFaculty().getId().equals(user.getFacultyId());
        if (!ownExam && !scope.canAccessBatchSubject(exam.getBatch().getId(), exam.getSubject().getId())) {
            throw new ForbiddenException("You do not have access to this exam");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void transition(PublicationStatus from, PublicationStatus to, String what) {
        if (!from.canMoveTo(to)) {
            throw new BusinessRuleException("A " + what + " cannot move from " + from + " to " + to);
        }
    }

    private void stampPublication(PublicationStatus target, java.util.function.Consumer<Instant> at,
                                  java.util.function.Consumer<Long> by) {
        if (target == PublicationStatus.PUBLISHED) {
            at.accept(Instant.now());
            by.accept(CurrentUser.idOrNull());
        } else {
            at.accept(null);
            by.accept(null);
        }
    }

    private Subject subjectOf(Batch batch, Long subjectId) {
        Subject subject = courseService.getSubject(subjectId);
        if (!subject.getCourse().getId().equals(batch.getCourse().getId())) {
            throw new BusinessRuleException(subject.getName() + " is not a subject of " + batch.getCourse().getName());
        }
        return subject;
    }

    private Map<Long, Student> activeStudents(Long batchId) {
        return studentRepository.findActiveByBatchId(batchId).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));
    }

    private Student requireInBatch(Map<Long, Student> students, Long studentId, String batchName) {
        Student student = students.get(studentId);
        if (student == null) {
            throw new BusinessRuleException("Student " + studentId + " is not an active student of " + batchName);
        }
        return student;
    }

    private boolean sameMarks(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private <T> List<Long> ids(Collection<T> items, Function<T, Long> id) {
        List<Long> ids = items.stream().map(id).toList();
        return ids.isEmpty() ? List.of(-1L) : ids;
    }

    private Map<Long, Long> counts(List<Object[]> rows) {
        Map<Long, Long> counts = new HashMap<>();
        rows.forEach(row -> counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue()));
        return counts;
    }

    private LocalDate rangeFrom(LocalDate from) {
        return from == null ? configService.today().minusMonths(6) : from;
    }

    private LocalDate rangeTo(LocalDate to) {
        return to == null ? configService.today().plusMonths(6) : to;
    }

    public AcademicTest getTest(Long id) {
        return testRepository.findDetail(id).orElseThrow(() -> ResourceNotFoundException.of("Test", id));
    }

    public Exam getExam(Long id) {
        return examRepository.findDetail(id).orElseThrow(() -> ResourceNotFoundException.of("Exam", id));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
