package com.coyotai.education.dashboard;

import com.coyotai.education.academic.AcademicDtos.BatchResponse;
import com.coyotai.education.academic.Batch;
import com.coyotai.education.academic.BatchRepository;
import com.coyotai.education.academic.CourseRepository;
import com.coyotai.education.assessment.AssessmentDtos.ExamResponse;
import com.coyotai.education.assessment.AssessmentDtos.TestResponse;
import com.coyotai.education.assessment.AssessmentService;
import com.coyotai.education.assessment.PublicationStatus;
import com.coyotai.education.attendance.AttendanceDtos.Record;
import com.coyotai.education.attendance.AttendanceRepository;
import com.coyotai.education.attendance.AttendanceStatus;
import com.coyotai.education.common.RecordStatus;
import com.coyotai.education.common.Ref;
import com.coyotai.education.dashboard.DashboardDtos.AttendanceToday;
import com.coyotai.education.dashboard.DashboardDtos.BatchSnapshot;
import com.coyotai.education.dashboard.DashboardDtos.FacultyDashboard;
import com.coyotai.education.dashboard.DashboardDtos.MentorDashboard;
import com.coyotai.education.dashboard.DashboardDtos.PendingMarks;
import com.coyotai.education.dashboard.DashboardDtos.SubjectSyllabus;
import com.coyotai.education.dashboard.DashboardDtos.Summary;
import com.coyotai.education.discipline.StudentFineRepository;
import com.coyotai.education.fee.FeeInstallmentRepository;
import com.coyotai.education.notification.Notification;
import com.coyotai.education.notification.NotificationService;
import com.coyotai.education.parentmeeting.ParentMeeting;
import com.coyotai.education.parentmeeting.ParentMeetingService;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.progress.ProgressCardService;
import com.coyotai.education.progress.ProgressCardService.Card;
import com.coyotai.education.schedule.ClassSchedule;
import com.coyotai.education.schedule.ClassScheduleRepository;
import com.coyotai.education.schedule.FacultyEntryExitRepository;
import com.coyotai.education.schedule.ScheduleDtos.EntryExitResponse;
import com.coyotai.education.schedule.ScheduleDtos.ScheduleResponse;
import com.coyotai.education.security.AppUserDetails;
import com.coyotai.education.security.CurrentUser;
import com.coyotai.education.security.DataScope;
import com.coyotai.education.security.DataScopeService;
import com.coyotai.education.staff.FacultyAssignmentRepository;
import com.coyotai.education.staff.FacultyRepository;
import com.coyotai.education.staff.MentorRepository;
import com.coyotai.education.student.StudentRepository;
import com.coyotai.education.syllabus.SyllabusService;
import com.coyotai.education.util.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Home screens for staff, mentors and faculty. Everything respects the caller's data scope. */
@Service
public class DashboardService {

    private final StudentRepository studentRepository;
    private final BatchRepository batchRepository;
    private final CourseRepository courseRepository;
    private final MentorRepository mentorRepository;
    private final FacultyRepository facultyRepository;
    private final FacultyAssignmentRepository assignmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final ClassScheduleRepository scheduleRepository;
    private final FacultyEntryExitRepository entryExitRepository;
    private final AssessmentService assessmentService;
    private final ProgressCardService progressCardService;
    private final ParentMeetingService parentMeetingService;
    private final SyllabusService syllabusService;
    private final StudentFineRepository fineRepository;
    private final FeeInstallmentRepository installmentRepository;
    private final NotificationService notificationService;
    private final DataScopeService dataScopeService;
    private final ProjectConfigService configService;

    public DashboardService(StudentRepository studentRepository, BatchRepository batchRepository,
                            CourseRepository courseRepository, MentorRepository mentorRepository,
                            FacultyRepository facultyRepository, FacultyAssignmentRepository assignmentRepository,
                            AttendanceRepository attendanceRepository, ClassScheduleRepository scheduleRepository,
                            FacultyEntryExitRepository entryExitRepository, AssessmentService assessmentService,
                            ProgressCardService progressCardService, ParentMeetingService parentMeetingService,
                            SyllabusService syllabusService, StudentFineRepository fineRepository,
                            FeeInstallmentRepository installmentRepository, NotificationService notificationService,
                            DataScopeService dataScopeService, ProjectConfigService configService) {
        this.studentRepository = studentRepository;
        this.batchRepository = batchRepository;
        this.courseRepository = courseRepository;
        this.mentorRepository = mentorRepository;
        this.facultyRepository = facultyRepository;
        this.assignmentRepository = assignmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.scheduleRepository = scheduleRepository;
        this.entryExitRepository = entryExitRepository;
        this.assessmentService = assessmentService;
        this.progressCardService = progressCardService;
        this.parentMeetingService = parentMeetingService;
        this.syllabusService = syllabusService;
        this.fineRepository = fineRepository;
        this.installmentRepository = installmentRepository;
        this.notificationService = notificationService;
        this.dataScopeService = dataScopeService;
        this.configService = configService;
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        DataScope scope = dataScopeService.current();
        AppUserDetails user = CurrentUser.require();
        LocalDate today = configService.today();
        boolean all = scope.isGlobal();
        Collection<Long> ids = scope.batchIdsForQuery();

        List<ScheduleResponse> classes = classesBetween(today, today, null, all, ids);
        List<ExamResponse> exams = assessmentService.exams(null, null, null, today, today.plusDays(7));
        long awaitingReview = assessmentService.tests(null, null, null, PublicationStatus.REVIEW, null, null).size()
                + assessmentService.exams(null, null, PublicationStatus.REVIEW, null, null).size();
        long cardsAwaitingReview = progressCardService.search(null, null, PublicationStatus.REVIEW).size();

        boolean fees = configService.isModuleEnabled(ModuleCode.FEES) && user.hasPermission("FEE_VIEW") && all;
        boolean messages = configService.isModuleEnabled(ModuleCode.NOTIFICATIONS) && user.hasPermission("NOTIFICATION_VIEW");

        return new Summary(
                today,
                studentRepository.countActive(all, ids),
                all ? batchRepository.countByStatus(Batch.Status.ACTIVE) : scope.batchIds().size(),
                courseRepository.countByStatus(RecordStatus.ACTIVE),
                mentorRepository.countByActiveTrue(),
                facultyRepository.countByActiveTrue(),
                attendanceToday(today, all, ids, scope),
                classes,
                exams,
                awaitingReview,
                cardsAwaitingReview,
                Money.scale(fineRepository.sumPending(all, ids)),
                fees ? Money.scale(installmentRepository.sumOutstanding()) : null,
                fees ? Money.scale(installmentRepository.sumOverdue()) : null,
                messages ? notificationService.countByStatus(Notification.Status.PENDING) : null,
                messages ? notificationService.countByStatus(Notification.Status.FAILED) : null,
                messages ? notificationService.recentExternal(8) : List.of());
    }

    @Transactional(readOnly = true)
    public MentorDashboard mentor() {
        DataScope scope = dataScopeService.current();
        LocalDate today = configService.today();
        Set<Long> mentorBatches = scope.mentorBatchIds();
        Collection<Long> ids = mentorBatches.isEmpty() ? List.of(-1L) : mentorBatches;
        Set<Long> markedToday = Set.copyOf(attendanceRepository.findMarkedBatchIds(today));

        Map<Long, Long> counts = new HashMap<>();
        studentRepository.countActiveByBatch().forEach(row -> counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue()));

        List<BatchSnapshot> batches = batchRepository.search(null, null, null, false, ids).stream()
                .map(batch -> new BatchSnapshot(BatchResponse.from(batch, counts.getOrDefault(batch.getId(), 0L)),
                        markedToday.contains(batch.getId()),
                        syllabusService.progressFor(batch, null).completion()))
                .toList();

        List<Record> absentOrLate = attendanceRepository.findAbsentOrLateOn(today, false, ids).stream()
                .map(Record::from).toList();
        List<TestResponse> recentTests = assessmentService.tests(null, null, null, null, today.minusDays(14), today)
                .stream().filter(test -> mentorBatches.contains(test.batch().id())).limit(10).toList();
        List<Card> pendingCards = new ArrayList<>();
        pendingCards.addAll(progressCardService.search(null, null, PublicationStatus.DRAFT));
        pendingCards.addAll(progressCardService.search(null, null, PublicationStatus.REVIEW));
        List<ExamResponse> exams = assessmentService.exams(null, null, null, today, today.plusDays(14)).stream()
                .filter(exam -> mentorBatches.contains(exam.batch().id())).toList();

        return new MentorDashboard(today, batches,
                batches.stream().mapToLong(b -> b.batch().studentCount()).sum(),
                attendanceToday(today, false, ids, scope),
                absentOrLate, recentTests,
                pendingCards.stream().filter(card -> card.batch() != null && mentorBatches.contains(card.batch().id())).toList(),
                exams,
                parentMeetingService.search(null, ParentMeeting.Status.SCHEDULED, today, today.plusDays(14)));
    }

    @Transactional(readOnly = true)
    public FacultyDashboard faculty() {
        AppUserDetails user = CurrentUser.require();
        DataScope scope = dataScopeService.current();
        LocalDate today = configService.today();
        Long facultyId = user.getFacultyId();
        if (facultyId == null) {
            return new FacultyDashboard(today, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        List<ScheduleResponse> todays = classesBetween(today, today, facultyId, true, List.of(-1L));
        List<ScheduleResponse> upcoming = classesBetween(today.plusDays(1), today.plusDays(7), facultyId, true, List.of(-1L));

        Map<Long, Ref> batches = new LinkedHashMap<>();
        Map<Long, Ref> subjects = new LinkedHashMap<>();
        Map<String, Ref[]> pairs = new LinkedHashMap<>();
        assignmentRepository.search(facultyId, null).stream().filter(a -> a.isActive()).forEach(a -> {
            Ref batch = Ref.of(a.getBatch().getId(), a.getBatch().getName());
            Ref subject = Ref.of(a.getSubject().getId(), a.getSubject().getName());
            batches.putIfAbsent(batch.id(), batch);
            subjects.putIfAbsent(subject.id(), subject);
            pairs.putIfAbsent(batch.id() + ":" + subject.id(), new Ref[]{batch, subject});
        });

        Map<Long, Long> studentCounts = new HashMap<>();
        studentRepository.countActiveByBatch().forEach(row -> studentCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue()));
        List<PendingMarks> pending = new ArrayList<>();
        assessmentService.tests(null, null, null, PublicationStatus.DRAFT, today.minusDays(30), today).stream()
                .filter(t -> scope.canAccessBatchSubject(t.batch().id(), t.subject().id()))
                .filter(t -> t.resultCount() < studentCounts.getOrDefault(t.batch().id(), 0L))
                .forEach(t -> pending.add(new PendingMarks("TEST", t.id(), t.title(), t.batch(), t.subject(), t.testDate(),
                        t.resultCount(), studentCounts.getOrDefault(t.batch().id(), 0L))));
        assessmentService.exams(null, null, PublicationStatus.DRAFT, today.minusDays(30), today).stream()
                .filter(e -> (e.faculty() != null && e.faculty().id().equals(facultyId))
                        || scope.canAccessBatchSubject(e.batch().id(), e.subject().id()))
                .filter(e -> e.resultCount() < studentCounts.getOrDefault(e.batch().id(), 0L))
                .forEach(e -> pending.add(new PendingMarks("EXAM", e.id(), e.name(), e.batch(), e.subject(), e.examDate(),
                        e.resultCount(), studentCounts.getOrDefault(e.batch().id(), 0L))));

        List<SubjectSyllabus> syllabus = new ArrayList<>();
        for (Ref[] pair : pairs.values()) {
            Batch batch = batchRepository.findDetail(pair[0].id()).orElse(null);
            if (batch == null) {
                continue;
            }
            syllabusService.progressFor(batch, pair[1].id()).subjects().forEach(s ->
                    syllabus.add(new SubjectSyllabus(pair[0], s.subject(), s.totalTopics(), s.completedTopics(), s.completion())));
        }

        List<EntryExitResponse> entries = entryExitRepository.search(today, today, facultyId).stream()
                .map(e -> new EntryExitResponse(e.getId(), Ref.of(e.getFaculty().getId(), e.getFaculty().getFullName()),
                        e.getEntryDate(), e.getSessionLabel(), e.getEntryTime(), e.getExitTime(), e.getRemarks()))
                .toList();

        return new FacultyDashboard(today, todays, upcoming, List.copyOf(batches.values()),
                List.copyOf(subjects.values()), pending, syllabus, entries);
    }

    private AttendanceToday attendanceToday(LocalDate today, boolean all, Collection<Long> ids, DataScope scope) {
        Map<AttendanceStatus, Long> counts = new EnumMap<>(AttendanceStatus.class);
        attendanceRepository.countByStatusOnDate(today, all, ids)
                .forEach(row -> counts.put((AttendanceStatus) row[0], ((Number) row[1]).longValue()));
        long marked = counts.values().stream().mapToLong(Long::longValue).sum();
        List<Long> markedBatches = attendanceRepository.findMarkedBatchIds(today);
        long batchesMarked = all ? markedBatches.size() : markedBatches.stream().filter(scope.batchIds()::contains).count();
        long activeBatches = all ? batchRepository.countByStatus(Batch.Status.ACTIVE) : scope.batchIds().size();
        return new AttendanceToday(counts.getOrDefault(AttendanceStatus.PRESENT, 0L),
                counts.getOrDefault(AttendanceStatus.ABSENT, 0L), counts.getOrDefault(AttendanceStatus.LATE, 0L),
                counts.getOrDefault(AttendanceStatus.EXCUSED, 0L), marked, batchesMarked, activeBatches);
    }

    private List<ScheduleResponse> classesBetween(LocalDate from, LocalDate to, Long facultyId, boolean all,
                                                  Collection<Long> ids) {
        return scheduleRepository.search(from, to, null, facultyId, all, ids).stream()
                .filter(s -> s.getStatus() != ClassSchedule.Status.CANCELLED)
                .map(ScheduleResponse::from).toList();
    }
}
