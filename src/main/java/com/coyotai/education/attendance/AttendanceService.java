package com.coyotai.education.attendance;

import com.coyotai.education.academic.Batch;
import com.coyotai.education.academic.BatchRepository;
import com.coyotai.education.academic.BatchService;
import com.coyotai.education.attendance.AttendanceDtos.BulkRequest;
import com.coyotai.education.attendance.AttendanceDtos.BulkResult;
import com.coyotai.education.attendance.AttendanceDtos.Record;
import com.coyotai.education.attendance.AttendanceDtos.Sheet;
import com.coyotai.education.attendance.AttendanceDtos.SheetRow;
import com.coyotai.education.attendance.AttendanceDtos.TakerClass;
import com.coyotai.education.attendance.AttendanceDtos.UpdateRequest;
import com.coyotai.education.audit.AuditService;
import com.coyotai.education.auth.User;
import com.coyotai.education.auth.UserRepository;
import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.common.PageResponse;
import com.coyotai.education.common.Ref;
import com.coyotai.education.common.ResourceNotFoundException;
import com.coyotai.education.discipline.DisciplineRecord;
import com.coyotai.education.discipline.DisciplineRecordRepository;
import com.coyotai.education.discipline.DisciplineType;
import com.coyotai.education.discipline.DisciplineTypeRepository;
import com.coyotai.education.discipline.StudentFineRepository;
import com.coyotai.education.notification.NotificationContent;
import com.coyotai.education.notification.NotificationEvent;
import com.coyotai.education.notification.NotificationMessageFactory;
import com.coyotai.education.notification.NotificationService;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.platform.RoleCode;
import com.coyotai.education.schedule.ClassSchedule;
import com.coyotai.education.schedule.ClassScheduleRepository;
import com.coyotai.education.schedule.ScheduleDtos.ScheduleResponse;
import com.coyotai.education.schedule.ScheduleService;
import com.coyotai.education.security.AppUserDetails;
import com.coyotai.education.security.CurrentUser;
import com.coyotai.education.security.DataScope;
import com.coyotai.education.security.DataScopeService;
import com.coyotai.education.student.Student;
import com.coyotai.education.student.StudentRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Attendance. Only mentors (for their batches) and faculty (for their classes) take it - see
 * {@link AttendanceAccess}. A whole sheet is saved in one transaction together with the
 * notification rows it produces; no provider is called here, so taking attendance succeeds
 * even when WhatsApp is unreachable.
 */
@Service
public class AttendanceService {

    /** Discipline type codes that observations on the sheet are recorded under. */
    static final String NO_UNIFORM_TYPE = "UNIFORM";
    static final String NO_ID_TAG_TYPE = "NAME_BADGE";

    private final AttendanceRepository repository;
    private final AttendanceAccess access;
    private final StudentRepository studentRepository;
    private final BatchService batchService;
    private final BatchRepository batchRepository;
    private final ScheduleService scheduleService;
    private final ClassScheduleRepository scheduleRepository;
    private final DataScopeService dataScopeService;
    private final DisciplineRecordRepository disciplineRecordRepository;
    private final DisciplineTypeRepository disciplineTypeRepository;
    private final StudentFineRepository fineRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationMessageFactory messageFactory;
    private final ProjectConfigService configService;
    private final AuditService auditService;

    public AttendanceService(AttendanceRepository repository, AttendanceAccess access, StudentRepository studentRepository,
                             BatchService batchService, BatchRepository batchRepository, ScheduleService scheduleService,
                             ClassScheduleRepository scheduleRepository, DataScopeService dataScopeService,
                             DisciplineRecordRepository disciplineRecordRepository,
                             DisciplineTypeRepository disciplineTypeRepository, StudentFineRepository fineRepository,
                             UserRepository userRepository, NotificationService notificationService,
                             NotificationMessageFactory messageFactory, ProjectConfigService configService,
                             AuditService auditService) {
        this.repository = repository;
        this.access = access;
        this.studentRepository = studentRepository;
        this.batchService = batchService;
        this.batchRepository = batchRepository;
        this.scheduleService = scheduleService;
        this.scheduleRepository = scheduleRepository;
        this.dataScopeService = dataScopeService;
        this.disciplineRecordRepository = disciplineRecordRepository;
        this.disciplineTypeRepository = disciplineTypeRepository;
        this.fineRepository = fineRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.messageFactory = messageFactory;
        this.configService = configService;
        this.auditService = auditService;
    }

    // ---- What the signed-in mentor or faculty member can take on a date ---------------------------

    @Transactional(readOnly = true)
    public List<TakerClass> classesToTake(LocalDate date) {
        AppUserDetails user = CurrentUser.require();
        Set<Long> mentorBatchIds = user.hasRole(RoleCode.MENTORS) && user.getMentorId() != null
                ? new HashSet<>(batchRepository.findIdsByMentorId(user.getMentorId())) : Set.of();
        Long facultyId = user.hasRole(RoleCode.FACULTY) ? user.getFacultyId() : null;

        List<Batch> mentorBatches = mentorBatchIds.isEmpty() ? List.of()
                : batchRepository.search(null, null, Batch.Status.ACTIVE, false, mentorBatchIds);

        Map<Long, ClassSchedule> classes = new LinkedHashMap<>();
        if (!mentorBatchIds.isEmpty()) {
            scheduleRepository.search(date, date, null, null, false, mentorBatchIds)
                    .forEach(schedule -> classes.put(schedule.getId(), schedule));
        }
        if (facultyId != null) {
            scheduleRepository.search(date, date, null, facultyId, true, List.of(-1L))
                    .forEach(schedule -> classes.putIfAbsent(schedule.getId(), schedule));
        }
        List<ClassSchedule> takeable = classes.values().stream()
                .filter(schedule -> schedule.getStatus() != ClassSchedule.Status.CANCELLED)
                .filter(schedule -> schedule.getBatch().getStatus() == Batch.Status.ACTIVE)
                .sorted(Comparator.comparing(ClassSchedule::getStartTime)
                        .thenComparing(schedule -> schedule.getBatch().getName()))
                .toList();

        Set<Long> batchIds = new HashSet<>();
        mentorBatches.forEach(batch -> batchIds.add(batch.getId()));
        takeable.forEach(schedule -> batchIds.add(schedule.getBatch().getId()));
        if (batchIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> students = new HashMap<>();
        studentRepository.countActiveByBatch()
                .forEach(row -> students.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue()));
        Map<String, Long> marks = new HashMap<>();
        repository.countMarks(date, batchIds)
                .forEach(row -> marks.put(sessionId(((Number) row[0]).longValue(), ((Number) row[1]).longValue()),
                        ((Number) row[2]).longValue()));

        List<TakerClass> result = new ArrayList<>();
        for (Batch batch : mentorBatches) {
            result.add(new TakerClass(Ref.of(batch.getId(), batch.getName()), null, null, null, null, null, null,
                    students.getOrDefault(batch.getId(), 0L).intValue(),
                    marks.getOrDefault(sessionId(batch.getId(), Attendance.WHOLE_DAY), 0L).intValue()));
        }
        for (ClassSchedule schedule : takeable) {
            Batch batch = schedule.getBatch();
            result.add(new TakerClass(Ref.of(batch.getId(), batch.getName()), schedule.getId(),
                    Ref.of(schedule.getSubject().getId(), schedule.getSubject().getName()),
                    schedule.getStartTime(), schedule.getEndTime(), schedule.getRoom(),
                    schedule.getFaculty() == null ? null
                            : Ref.of(schedule.getFaculty().getId(), schedule.getFaculty().getFullName()),
                    students.getOrDefault(batch.getId(), 0L).intValue(),
                    marks.getOrDefault(sessionId(batch.getId(), schedule.getId()), 0L).intValue()));
        }
        return result;
    }

    // Saved attendance is historical: later profile/topic edits must not rewrite it.
    private boolean exempt(Student student, ClassSchedule schedule, Attendance existing) {
        if (existing != null) return existing.getStatus() == AttendanceStatus.HOLIDAY;
        return schedule != null && schedule.getTopic() != null
                && !schedule.getTopic().requires(student);
    }

    private static String sessionId(long batchId, long sessionKey) {
        return batchId + ":" + sessionKey;
    }

    // ---- Sheet --------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Sheet sheet(Long batchId, LocalDate date, Long scheduleId) {
        Batch batch = batchService.getBatch(batchId);
        ClassSchedule schedule = scheduleId == null ? null : scheduleOf(batchId, date, scheduleId);
        boolean taker = access.canTake(batch, schedule);
        if (!taker) {
            // Everyone else reads attendance within their normal data scope.
            dataScopeService.current().requireBatch(batchId);
        }
        long sessionKey = schedule == null ? Attendance.WHOLE_DAY : schedule.getId();

        Map<Long, Attendance> existing = new HashMap<>();
        repository.findSheet(batchId, date, sessionKey)
                .forEach(attendance -> existing.put(attendance.getStudent().getId(), attendance));

        List<Student> activeStudents = studentRepository.findActiveByBatchId(batchId);
        List<Ref> holidays = activeStudents.stream().filter(s -> exempt(s, schedule, existing.get(s.getId())))
                .map(s -> Ref.of(s.getId(), s.getFullName())).toList();
        List<SheetRow> rows = activeStudents.stream()
                .filter(s -> !exempt(s, schedule, existing.get(s.getId())))
                .map(student -> {
                    Attendance mark = existing.get(student.getId());
                    return new SheetRow(mark == null ? null : mark.getId(), student.getId(), student.getAdmissionNumber(),
                            student.getFullName(), student.getPhotoUrl(),
                            mark == null ? null : mark.getStatus(), mark == null ? null : mark.getLateMinutes(),
                            mark == null ? null : mark.getAbsenceReason(), mark != null && mark.isNoUniform(),
                            mark != null && mark.isNoIdTag(), mark == null ? null : mark.getRemarks(),
                            notificationService.canMessageParent(student.getParent()));
                })
                .toList();

        Attendance latest = existing.values().stream().max(Comparator.comparing(Attendance::getMarkedAt)).orElse(null);
        String markedBy = latest == null || latest.getMarkedBy() == null ? null
                : userRepository.findById(latest.getMarkedBy()).map(User::getFullName).orElse(null);

        List<ScheduleResponse> classesThatDay = scheduleRepository
                .search(date, date, batchId, null, true, List.of(-1L)).stream()
                .filter(s -> s.getStatus() != ClassSchedule.Status.CANCELLED)
                .map(ScheduleResponse::from).toList();

        return new Sheet(Ref.of(batch.getId(), batch.getName()), date,
                schedule == null ? null : ScheduleResponse.from(schedule),
                !existing.isEmpty(), latest == null ? null : latest.getMarkedAt(), markedBy,
                taker && !date.isAfter(configService.today()), classesThatDay, rows, holidays);
    }

    // ---- Taking attendance --------------------------------------------------------------------------

    @Transactional
    public BulkResult saveBulk(BulkRequest request) {
        if (request.date().isAfter(configService.today())) {
            throw new BusinessRuleException("Attendance cannot be taken for a future date");
        }
        Batch batch = batchService.getBatch(request.batchId());
        ClassSchedule schedule = request.scheduleId() == null ? null
                : scheduleOf(request.batchId(), request.date(), request.scheduleId());
        access.requireCanTake(batch, schedule);
        long sessionKey = schedule == null ? Attendance.WHOLE_DAY : schedule.getId();

        Map<Long, Student> students = new LinkedHashMap<>();
        studentRepository.findActiveByBatchId(batch.getId()).forEach(student -> students.put(student.getId(), student));
        Set<Long> seen = new HashSet<>();
        for (BulkRequest.Entry entry : request.entries()) {
            if (!students.containsKey(entry.studentId())) {
                throw new BusinessRuleException("Student " + entry.studentId() + " is not an active student of " + batch.getName());
            }
            if (!seen.add(entry.studentId())) {
                throw new BusinessRuleException("A student appears twice in the attendance sheet");
            }
        }

        Map<Long, Attendance> existing = new HashMap<>();
        repository.findSheet(batch.getId(), request.date(), sessionKey)
                .forEach(attendance -> existing.put(attendance.getStudent().getId(), attendance));
        List<BulkRequest.Entry> entries = new ArrayList<>();
        for (BulkRequest.Entry entry : request.entries()) {
            if (!exempt(students.get(entry.studentId()), schedule, existing.get(entry.studentId()))) {
                if (entry.status() == AttendanceStatus.HOLIDAY) throw new BusinessRuleException("Holiday is assigned automatically by topic eligibility");
                entries.add(entry);
            }
        }
        for (Student student : students.values()) {
            if (exempt(student, schedule, existing.get(student.getId()))) {
                entries.add(new BulkRequest.Entry(student.getId(), AttendanceStatus.HOLIDAY, null, null, false, false, "Not required for this topic"));
            } else if (!seen.contains(student.getId())) {
                throw new BusinessRuleException("Mark every eligible student before saving");
            }
        }
        if (entries.isEmpty()) throw new BusinessRuleException("There are no active students to mark");
        Map<Long, List<DisciplineRecord>> linked = linkedRecords(existing.values());
        Observations observations = new Observations();

        Long markedBy = CurrentUser.idOrNull();
        Instant now = Instant.now();
        int created = 0;
        int updated = 0;
        int queued = 0;
        int skipped = 0;
        Map<AttendanceStatus, Integer> tally = new EnumMap<>(AttendanceStatus.class);

        for (BulkRequest.Entry entry : entries) {
            Student student = students.get(entry.studentId());
            Attendance attendance = existing.get(student.getId());
            AttendanceStatus previous = attendance == null ? null : attendance.getStatus();
            if (attendance == null) {
                attendance = new Attendance();
                attendance.setStudent(student);
                attendance.setBatch(batch);
                attendance.setClassSchedule(schedule);
                attendance.setSessionKey(sessionKey);
                attendance.setAttendanceDate(request.date());
                created++;
            } else {
                updated++;
            }
            // The sheet sends every detail, so a missing observation means "not observed".
            apply(attendance, entry.status(), entry.lateMinutes(), entry.absenceReason(),
                    Boolean.TRUE.equals(entry.noUniform()), Boolean.TRUE.equals(entry.noIdTag()), entry.remarks());
            attendance.setMarkedBy(markedBy);
            attendance.setMarkedAt(now);
            tally.merge(entry.status(), 1, Integer::sum);

            int[] outcome = decideNotification(attendance, previous, batch, schedule);
            queued += outcome[0];
            skipped += outcome[1];
            repository.save(attendance);
            observations.sync(attendance, linked.getOrDefault(attendance.getId(), List.of()));
        }

        auditService.record("Attendance", batch.getId(), AuditService.UPDATE,
                "Attendance for " + batch.getName() + (schedule == null ? " (whole day)" : " / " + schedule.getSubject().getName())
                        + " on " + request.date() + ": " + tally + " (" + created + " new, " + updated + " changed"
                        + (observations.recorded > 0 ? ", " + observations.recorded + " observations recorded" : "") + ")");
        return new BulkResult(entries.size(), created, updated, queued, skipped, observations.recorded);
    }

    /** Correction of one mark, by a mentor or faculty member who may take that attendance. */
    @Transactional
    public Record update(Long id, UpdateRequest request) {
        Attendance attendance = repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Attendance", id));
        access.requireCanTake(attendance.getBatch(), attendance.getClassSchedule());
        if (attendance.getStatus() == AttendanceStatus.HOLIDAY || request.status() == AttendanceStatus.HOLIDAY) {
            throw new BusinessRuleException("Topic holidays cannot be changed manually");
        }
        AttendanceStatus previous = attendance.getStatus();

        // A correction replaces the mark with everything it records, like saving the sheet does.
        apply(attendance, request.status(), request.lateMinutes(), request.absenceReason(),
                Boolean.TRUE.equals(request.noUniform()), Boolean.TRUE.equals(request.noIdTag()), request.remarks());
        attendance.setMarkedBy(CurrentUser.idOrNull());
        attendance.setMarkedAt(Instant.now());
        decideNotification(attendance, previous, attendance.getBatch(), attendance.getClassSchedule());
        new Observations().sync(attendance, linkedRecords(List.of(attendance)).getOrDefault(id, List.of()));

        auditService.record("Attendance", id, AuditService.UPDATE,
                "Changed attendance of " + attendance.getStudent().getFullName() + " on "
                        + attendance.getAttendanceDate() + " from " + previous + " to " + request.status());
        return Record.from(attendance, true);
    }

    /** Keeps the details consistent with the status: minutes only when late, a reason only when away. */
    private void apply(Attendance attendance, AttendanceStatus status, Integer lateMinutes, AbsenceReason reason,
                       boolean noUniform, boolean noIdTag, String remarks) {
        attendance.setStatus(status);
        attendance.setLateMinutes(status == AttendanceStatus.LATE ? lateMinutes : null);
        attendance.setAbsenceReason(status.isAway() ? (reason == null ? AbsenceReason.NOT_INFORMED : reason) : null);
        // Nothing can be observed about a student who is not there.
        attendance.setNoUniform(status.countsAsAttended() && noUniform);
        attendance.setNoIdTag(status.countsAsAttended() && noIdTag);
        attendance.setRemarks(blankToNull(remarks));
    }

    private Map<Long, List<DisciplineRecord>> linkedRecords(Collection<Attendance> marks) {
        List<Long> ids = marks.stream().map(Attendance::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return disciplineRecordRepository.findByAttendanceIds(ids).stream()
                .collect(Collectors.groupingBy(record -> record.getAttendance().getId()));
    }

    /**
     * Turns observations on a mark into discipline records - one per observation, however often
     * the sheet is saved. Withdrawing an observation removes its record again, unless somebody
     * has already acted on it (resolved it or raised a fine).
     */
    private final class Observations {

        private final Map<String, DisciplineType> types = new HashMap<>();
        private int recorded;

        void sync(Attendance attendance, List<DisciplineRecord> linked) {
            sync(attendance, linked, NO_UNIFORM_TYPE, attendance.isNoUniform(), "No uniform");
            sync(attendance, linked, NO_ID_TAG_TYPE, attendance.isNoIdTag(), "No ID tag");
        }

        private void sync(Attendance attendance, List<DisciplineRecord> linked, String typeCode, boolean observed,
                          String label) {
            DisciplineRecord record = linked.stream()
                    .filter(candidate -> candidate.getDisciplineType().getCode().equalsIgnoreCase(typeCode))
                    .findFirst().orElse(null);
            if (observed && record == null) {
                DisciplineType type = types.computeIfAbsent(typeCode,
                        code -> disciplineTypeRepository.findFirstByCodeIgnoreCaseAndActiveTrue(code).orElse(null));
                if (type == null) {
                    return; // this client does not use that discipline type
                }
                DisciplineRecord created = new DisciplineRecord();
                created.setStudent(attendance.getStudent());
                created.setBatch(attendance.getBatch());
                created.setAttendance(attendance);
                created.setDisciplineType(type);
                created.setIncidentDate(attendance.getAttendanceDate());
                created.setDescription(label + " - noted while taking attendance");
                created.setStatus(DisciplineRecord.Status.OPEN);
                disciplineRecordRepository.save(created);
                recorded++;
            } else if (!observed && record != null && record.getStatus() == DisciplineRecord.Status.OPEN
                    && !fineRepository.existsByDisciplineRecordId(record.getId())) {
                disciplineRecordRepository.delete(record);
            }
        }
    }

    /**
     * Queues a parent message when the mark newly becomes ABSENT or LATE. Re-saving the same
     * status never sends a second message. Returns {queued, skipped}.
     */
    private int[] decideNotification(Attendance attendance, AttendanceStatus previous, Batch batch, ClassSchedule schedule) {
        AttendanceStatus status = attendance.getStatus();
        NotificationEvent event = status.notificationEvent();
        if (event == null) {
            attendance.setNotificationStatus(Attendance.NotificationDecision.NOT_REQUIRED);
            return new int[]{0, 0};
        }
        if (status == previous && attendance.getNotificationStatus() == Attendance.NotificationDecision.QUEUED) {
            return new int[]{0, 0};
        }
        Student student = attendance.getStudent();
        String parentName = student.getParent() == null ? "Parent" : student.getParent().getName();
        String className = batch.getName() + (schedule == null ? "" : " - " + schedule.getSubject().getName());
        NotificationContent content = status == AttendanceStatus.ABSENT
                ? messageFactory.absent(parentName, student.getFullName(), className, attendance.getAttendanceDate())
                : messageFactory.late(parentName, student.getFullName(), className, attendance.getAttendanceDate());
        NotificationService.PublishResult result = notificationService.publish(event, student, content);
        attendance.setNotificationStatus(result.anyQueued()
                ? Attendance.NotificationDecision.QUEUED : Attendance.NotificationDecision.SKIPPED);
        return result.anyQueued() ? new int[]{1, 0} : new int[]{0, 1};
    }

    // ---- Reading ----------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<Record> history(Long studentId, Long batchId, AttendanceStatus status, LocalDate from,
                                        LocalDate to, Pageable pageable) {
        DataScope scope = dataScopeService.current();
        if (batchId != null) {
            scope.requireBatch(batchId);
        }
        if (studentId != null) {
            Student student = studentRepository.findDetail(studentId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Student", studentId));
            dataScopeService.requireStudent(student);
        }
        LocalDate end = to == null ? configService.today() : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        if (end.isBefore(start)) {
            throw new BusinessRuleException("The end date must not be before the start date");
        }
        AppUserDetails user = CurrentUser.require();
        return PageResponse.of(repository.history(studentId, batchId, status, start, end, scope.isGlobal(),
                        scope.batchIdsForQuery(), pageable),
                mark -> Record.from(mark, AttendanceAccess.canTake(user, mark.getBatch(), mark.getClassSchedule())));
    }

    @Transactional(readOnly = true)
    public AttendanceSummary summaryFor(Long studentId, LocalDate from, LocalDate to) {
        Map<AttendanceStatus, Long> counts = new EnumMap<>(AttendanceStatus.class);
        for (Object[] row : repository.countByStatusForStudent(studentId, from, to)) {
            counts.put((AttendanceStatus) row[0], ((Number) row[1]).longValue());
        }
        return AttendanceSummary.fromCounts(counts);
    }

    private ClassSchedule scheduleOf(Long batchId, LocalDate date, Long scheduleId) {
        ClassSchedule schedule = scheduleService.get(scheduleId);
        if (!schedule.getBatch().getId().equals(batchId) || !schedule.getScheduleDate().equals(date)) {
            throw new BusinessRuleException("The selected class is not scheduled for this batch on this date");
        }
        if (schedule.getStatus() == ClassSchedule.Status.CANCELLED) {
            throw new BusinessRuleException("This class was cancelled");
        }
        return schedule;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
