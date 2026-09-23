package com.coyotai.education.student.portal;

import com.coyotai.education.assessment.AcademicTest;
import com.coyotai.education.assessment.AcademicTestResultRepository;
import com.coyotai.education.assessment.ExamRepository;
import com.coyotai.education.assessment.ExamResultRepository;
import com.coyotai.education.assessment.MarksValidator;
import com.coyotai.education.attendance.AttendanceRepository;
import com.coyotai.education.attendance.AttendanceService;
import com.coyotai.education.attendance.AttendanceSummary;
import com.coyotai.education.common.ForbiddenException;
import com.coyotai.education.common.Ref;
import com.coyotai.education.discipline.DisciplineDtos.FineResponse;
import com.coyotai.education.discipline.DisciplineDtos.RecordResponse;
import com.coyotai.education.discipline.DisciplineRecordRepository;
import com.coyotai.education.discipline.StudentFineRepository;
import com.coyotai.education.fee.FeeDtos.FeeSummary;
import com.coyotai.education.fee.FeeService;
import com.coyotai.education.notification.NotificationDtos.Inbox;
import com.coyotai.education.notification.NotificationService;
import com.coyotai.education.performance.GradeScale;
import com.coyotai.education.performance.PerformanceService;
import com.coyotai.education.performance.PerformanceService.StudentPerformance;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.progress.ProgressCardService;
import com.coyotai.education.progress.ProgressCardService.Card;
import com.coyotai.education.schedule.ClassSchedule;
import com.coyotai.education.schedule.ClassScheduleRepository;
import com.coyotai.education.schedule.ScheduleDtos.ScheduleResponse;
import com.coyotai.education.security.AppUserDetails;
import com.coyotai.education.security.CurrentUser;
import com.coyotai.education.student.Student;
import com.coyotai.education.student.StudentDtos.StudentDetail;
import com.coyotai.education.student.StudentService;
import com.coyotai.education.student.portal.StudentPortalDtos.AttendanceEntry;
import com.coyotai.education.student.portal.StudentPortalDtos.Dashboard;
import com.coyotai.education.student.portal.StudentPortalDtos.ExamResult;
import com.coyotai.education.student.portal.StudentPortalDtos.MyAttendance;
import com.coyotai.education.student.portal.StudentPortalDtos.MyExams;
import com.coyotai.education.student.portal.StudentPortalDtos.TestResult;
import com.coyotai.education.student.portal.StudentPortalDtos.UpcomingExam;
import com.coyotai.education.syllabus.SyllabusService;
import com.coyotai.education.syllabus.SyllabusService.BatchSyllabus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * The student's own records. The student is always taken from the signed-in account, never
 * from the request, so one student can never ask for another's data. Results appear only
 * once published.
 */
@Service
public class StudentPortalService {

    private final StudentService studentService;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceService attendanceService;
    private final AcademicTestResultRepository testResultRepository;
    private final ExamResultRepository examResultRepository;
    private final ExamRepository examRepository;
    private final ClassScheduleRepository scheduleRepository;
    private final PerformanceService performanceService;
    private final ProgressCardService progressCardService;
    private final DisciplineRecordRepository disciplineRepository;
    private final StudentFineRepository fineRepository;
    private final FeeService feeService;
    private final SyllabusService syllabusService;
    private final NotificationService notificationService;
    private final GradeScale gradeScale;
    private final ProjectConfigService configService;

    public StudentPortalService(StudentService studentService, AttendanceRepository attendanceRepository,
                                AttendanceService attendanceService, AcademicTestResultRepository testResultRepository,
                                ExamResultRepository examResultRepository, ExamRepository examRepository,
                                ClassScheduleRepository scheduleRepository, PerformanceService performanceService,
                                ProgressCardService progressCardService, DisciplineRecordRepository disciplineRepository,
                                StudentFineRepository fineRepository, FeeService feeService,
                                SyllabusService syllabusService, NotificationService notificationService,
                                GradeScale gradeScale, ProjectConfigService configService) {
        this.studentService = studentService;
        this.attendanceRepository = attendanceRepository;
        this.attendanceService = attendanceService;
        this.testResultRepository = testResultRepository;
        this.examResultRepository = examResultRepository;
        this.examRepository = examRepository;
        this.scheduleRepository = scheduleRepository;
        this.performanceService = performanceService;
        this.progressCardService = progressCardService;
        this.disciplineRepository = disciplineRepository;
        this.fineRepository = fineRepository;
        this.feeService = feeService;
        this.syllabusService = syllabusService;
        this.notificationService = notificationService;
        this.gradeScale = gradeScale;
        this.configService = configService;
    }

    /** The signed-in student. Staff accounts have no student profile and are refused. */
    private Student me() {
        AppUserDetails user = CurrentUser.require();
        if (user.getStudentId() == null) {
            throw new ForbiddenException("No student profile is linked to this account");
        }
        return studentService.getDetail(user.getStudentId());
    }

    @Transactional(readOnly = true)
    public StudentDetail profile() {
        return StudentDetail.from(me());
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        Student student = me();
        LocalDate today = configService.today();
        StudentPerformance performance = performanceService.compute(student, null, null);
        boolean feesEnabled = configService.isModuleEnabled(ModuleCode.FEES);
        FeeSummary fees = feesEnabled ? feeService.summary(student.getId()) : null;
        var batch = student.getBatch();
        return new Dashboard(
                student.getFullName(),
                student.getAdmissionNumber(),
                student.getCourse() == null ? null : Ref.of(student.getCourse().getId(), student.getCourse().getName()),
                batch == null ? null : Ref.of(batch.getId(), batch.getName()),
                batch == null || batch.getMentor() == null ? null : Ref.of(batch.getMentor().getId(), batch.getMentor().getFullName()),
                student.getAcademicYear() == null ? null : Ref.of(student.getAcademicYear().getId(), student.getAcademicYear().getName()),
                performance.attendancePercentage(),
                performance.overall(),
                performance.grade(),
                fineRepository.sumPendingForStudent(student.getId()),
                fees == null ? null : fees.outstanding(),
                fees == null ? null : fees.nextDueAmount(),
                fees == null ? null : fees.nextDueDate(),
                upcomingExams(student, today, 3),
                classesBetween(student, today, today),
                exams().results().stream().limit(3).toList(),
                student.getUser() == null ? 0 : notificationService.inbox(student.getUser().getId(), 1).unread(),
                feesEnabled);
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> schedule(LocalDate from, LocalDate to) {
        Student student = me();
        LocalDate start = from == null ? configService.today() : from;
        LocalDate end = to == null ? start.plusDays(6) : to;
        return classesBetween(student, start, end);
    }

    @Transactional(readOnly = true)
    public MyAttendance attendance(LocalDate from, LocalDate to) {
        Student student = me();
        LocalDate end = to == null ? configService.today() : to;
        LocalDate start = from == null ? defaultStart(student, end) : from;
        AttendanceSummary summary = attendanceService.summaryFor(student.getId(), start, end);
        List<AttendanceEntry> history = attendanceRepository
                .history(student.getId(), null, null, start, end, true, List.of(-1L), PageRequest.of(0, 500))
                .getContent().stream()
                .map(a -> new AttendanceEntry(a.getAttendanceDate(),
                        a.getClassSchedule() == null ? null
                                : Ref.of(a.getClassSchedule().getSubject().getId(), a.getClassSchedule().getSubject().getName()),
                        a.getStatus(), a.getRemarks()))
                .toList();
        return new MyAttendance(start, end, summary, history);
    }

    @Transactional(readOnly = true)
    public List<TestResult> tests(AcademicTest.Type type) {
        Student student = me();
        LocalDate end = configService.today();
        return testResultRepository.findPublishedForStudent(student.getId(), type, end.minusYears(2), end).stream()
                .map(r -> {
                    var test = r.getTest();
                    Double pct = r.isAbsent() ? null : MarksValidator.percentage(r.getMarksObtained(), test.getMaxMarks());
                    return new TestResult(test.getTestDate(), test.getTestType().name(), test.getTitle(),
                            Ref.of(test.getSubject().getId(), test.getSubject().getName()), test.getWeekNumber(),
                            r.getMarksObtained(), test.getMaxMarks(), r.isAbsent(), pct, gradeScale.gradeFor(pct),
                            r.getRemarks());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public MyExams exams() {
        Student student = me();
        LocalDate today = configService.today();
        List<ExamResult> results = examResultRepository.findPublishedForStudent(student.getId(), today.minusYears(2), today.plusYears(1))
                .stream()
                .map(r -> {
                    var exam = r.getExam();
                    Double pct = r.isAbsent() ? null : MarksValidator.percentage(r.getMarksObtained(), exam.getMaxMarks());
                    String outcome = r.isAbsent() ? "ABSENT"
                            : r.getMarksObtained().compareTo(exam.getPassingMarks()) >= 0 ? "PASS" : "FAIL";
                    return new ExamResult(exam.getExamDate(), exam.getName(), exam.getExamType().getName(),
                            Ref.of(exam.getSubject().getId(), exam.getSubject().getName()), r.getMarksObtained(),
                            exam.getMaxMarks(), exam.getPassingMarks(), r.isAbsent(), pct, r.getGrade(), outcome,
                            r.getRemarks());
                })
                .toList();
        return new MyExams(upcomingExams(student, today, 10), results);
    }

    @Transactional(readOnly = true)
    public StudentPerformance performance(LocalDate from, LocalDate to) {
        return performanceService.compute(me(), from, to);
    }

    @Transactional(readOnly = true)
    public List<Card> progressCards() {
        return progressCardService.publishedForStudent(me().getId());
    }

    @Transactional(readOnly = true)
    public Card progressCard(Long id) {
        return progressCardService.publishedDetail(id, me().getId());
    }

    @Transactional(readOnly = true)
    public List<RecordResponse> discipline() {
        Student student = me();
        LocalDate end = configService.today();
        return disciplineRepository.findForStudent(student.getId(), end.minusYears(2), end).stream()
                .map(RecordResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FineResponse> fines() {
        Student student = me();
        return fineRepository.findAllByStudentIdOrderByFineDateDesc(student.getId()).stream()
                .map(FineResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public FeeSummary fees() {
        return feeService.summary(me().getId());
    }

    @Transactional(readOnly = true)
    public BatchSyllabus syllabus() {
        Student student = me();
        if (!configService.academics().isStudentSyllabusVisible()) {
            throw new ForbiddenException("Syllabus progress is not shared with students");
        }
        if (student.getBatch() == null) {
            throw new ForbiddenException("You are not assigned to a batch yet");
        }
        return syllabusService.progressFor(student.getBatch(), null);
    }

    @Transactional(readOnly = true)
    public Inbox notifications() {
        Student student = me();
        return student.getUser() == null ? new Inbox(0, List.of()) : notificationService.inbox(student.getUser().getId(), 50);
    }

    @Transactional
    public void markNotificationsRead() {
        Student student = me();
        if (student.getUser() != null) {
            notificationService.markAllRead(student.getUser().getId());
        }
    }

    private List<ScheduleResponse> classesBetween(Student student, LocalDate from, LocalDate to) {
        if (student.getBatch() == null) {
            return List.of();
        }
        return scheduleRepository.search(from, to, student.getBatch().getId(), null, true, List.of(-1L)).stream()
                .filter(s -> s.getStatus() != ClassSchedule.Status.CANCELLED)
                .map(ScheduleResponse::from).toList();
    }

    private List<UpcomingExam> upcomingExams(Student student, LocalDate from, int limit) {
        if (student.getBatch() == null) {
            return List.of();
        }
        return examRepository.upcoming(from, from.plusMonths(3), false, List.of(student.getBatch().getId())).stream()
                .limit(limit)
                .map(e -> new UpcomingExam(e.getId(), e.getExamDate(), e.getStartTime(), e.getEndTime(), e.getName(),
                        e.getExamType().getName(), Ref.of(e.getSubject().getId(), e.getSubject().getName())))
                .toList();
    }

    private LocalDate defaultStart(Student student, LocalDate end) {
        if (student.getAcademicYear() != null && student.getAcademicYear().getStartDate().isBefore(end)) {
            return student.getAcademicYear().getStartDate();
        }
        return end.minusMonths(6);
    }
}
