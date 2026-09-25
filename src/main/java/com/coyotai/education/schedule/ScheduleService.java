package com.coyotai.education.schedule;

import com.coyotai.education.academic.Batch;
import com.coyotai.education.academic.BatchService;
import com.coyotai.education.academic.CourseService;
import com.coyotai.education.academic.Subject;
import com.coyotai.education.audit.AuditService;
import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.common.ResourceNotFoundException;
import com.coyotai.education.notification.NotificationEvent;
import com.coyotai.education.notification.NotificationMessageFactory;
import com.coyotai.education.notification.NotificationService;
import com.coyotai.education.schedule.ScheduleDtos.Conflict;
import com.coyotai.education.schedule.ScheduleDtos.ScheduleRequest;
import com.coyotai.education.schedule.ScheduleDtos.ScheduleResponse;
import com.coyotai.education.security.AppUserDetails;
import com.coyotai.education.security.CurrentUser;
import com.coyotai.education.security.DataScope;
import com.coyotai.education.security.DataScopeService;
import com.coyotai.education.staff.Faculty;
import com.coyotai.education.staff.StaffService;
import com.coyotai.education.student.Student;
import com.coyotai.education.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Class schedule. Every create or change is checked for faculty, batch and room clashes. */
@Service
public class ScheduleService {

    /** Guard against a mistyped "repeat until" creating years of classes. */
    private static final int MAX_OCCURRENCES = 60;

    private final ClassScheduleRepository repository;
    private final BatchService batchService;
    private final CourseService courseService;
    private final StaffService staffService;
    private final StudentRepository studentRepository;
    private final DataScopeService dataScopeService;
    private final NotificationService notificationService;
    private final NotificationMessageFactory messageFactory;
    private final AuditService auditService;
    private final com.coyotai.education.syllabus.SyllabusTopicRepository topics;

    public ScheduleService(ClassScheduleRepository repository, BatchService batchService, CourseService courseService,
                           StaffService staffService, StudentRepository studentRepository,
                           DataScopeService dataScopeService, NotificationService notificationService,
                           NotificationMessageFactory messageFactory, AuditService auditService, com.coyotai.education.syllabus.SyllabusTopicRepository topics) {
        this.repository = repository;
        this.topics = topics;
        this.batchService = batchService;
        this.courseService = courseService;
        this.staffService = staffService;
        this.studentRepository = studentRepository;
        this.dataScopeService = dataScopeService;
        this.notificationService = notificationService;
        this.messageFactory = messageFactory;
        this.auditService = auditService;
    }

    /** Schedule in a date range. {@code mine} narrows faculty users to their own classes. */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> search(LocalDate from, LocalDate to, Long batchId, Long facultyId, boolean mine) {
        requireRange(from, to);
        DataScope scope = dataScopeService.current();
        if (batchId != null) {
            scope.requireBatch(batchId);
        }
        Long effectiveFaculty = facultyId;
        if (mine) {
            AppUserDetails user = CurrentUser.require();
            if (user.getFacultyId() == null) {
                return List.of();
            }
            effectiveFaculty = user.getFacultyId();
        }
        return repository.search(from, to, batchId, effectiveFaculty, scope.isGlobal(), scope.batchIdsForQuery())
                .stream().map(ScheduleResponse::from).toList();
    }

    /** Dry run used by the UI before saving. */
    @Transactional(readOnly = true)
    public List<Conflict> checkConflicts(ScheduleRequest request, Long excludeId) {
        validate(request);
        List<Conflict> conflicts = new ArrayList<>();
        for (LocalDate date : occurrences(request)) {
            conflicts.addAll(conflictsOn(date, request, excludeId));
        }
        return conflicts;
    }

    @Transactional
    public List<ScheduleResponse> create(ScheduleRequest request) {
        validate(request);
        Batch batch = batchService.getBatch(request.batchId());
        Subject subject = subjectOf(batch, request.subjectId());
        Faculty faculty = request.facultyId() == null ? null : staffService.getFaculty(request.facultyId());

        List<LocalDate> dates = occurrences(request);
        List<Conflict> conflicts = new ArrayList<>();
        for (LocalDate date : dates) {
            conflicts.addAll(conflictsOn(date, request, null));
        }
        if (!conflicts.isEmpty()) {
            throw new BusinessRuleException("Schedule conflict: " + conflicts.get(0).message()
                    + (conflicts.size() > 1 ? " (and " + (conflicts.size() - 1) + " more)" : ""));
        }

        List<ScheduleResponse> created = new ArrayList<>();
        for (LocalDate date : dates) {
            ClassSchedule schedule = new ClassSchedule();
            schedule.setBatch(batch);
            schedule.setCourse(batch.getCourse());
            schedule.setSubject(subject);
            schedule.setFaculty(faculty);
            schedule.setScheduleDate(date);
            schedule.setStartTime(request.startTime());
            schedule.setEndTime(request.endTime());
            schedule.setRoom(blankToNull(request.room()));
            schedule.setNotes(blankToNull(request.notes()));
            schedule.setTopic(topicOf(request));
            schedule.setStatus(ClassSchedule.Status.SCHEDULED);
            repository.save(schedule);
            created.add(ScheduleResponse.from(schedule));
        }
        auditService.record("ClassSchedule", created.get(0).id(), AuditService.CREATE,
                "Scheduled " + subject.getName() + " for " + batch.getName() + " (" + created.size() + " class"
                        + (created.size() == 1 ? "" : "es") + ")");
        return created;
    }

    @Transactional
    public ScheduleResponse update(Long id, ScheduleRequest request) {
        validate(request);
        ClassSchedule schedule = get(id);
        dataScopeService.current().requireBatch(schedule.getBatch().getId());
        List<Conflict> conflicts = conflictsOn(request.scheduleDate(), request, id);
        if (!conflicts.isEmpty()) {
            throw new BusinessRuleException("Schedule conflict: " + conflicts.get(0).message());
        }
        boolean timingChanged = !schedule.getScheduleDate().equals(request.scheduleDate())
                || !schedule.getStartTime().equals(request.startTime())
                || !schedule.getEndTime().equals(request.endTime());
        ClassSchedule.Status newStatus = request.status() == null ? schedule.getStatus() : request.status();
        boolean cancelled = newStatus == ClassSchedule.Status.CANCELLED && schedule.getStatus() != ClassSchedule.Status.CANCELLED;

        Batch batch = batchService.getBatch(request.batchId());
        schedule.setBatch(batch);
        schedule.setCourse(batch.getCourse());
        schedule.setSubject(subjectOf(batch, request.subjectId()));
        schedule.setFaculty(request.facultyId() == null ? null : staffService.getFaculty(request.facultyId()));
        schedule.setScheduleDate(request.scheduleDate());
        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
        schedule.setRoom(blankToNull(request.room()));
        schedule.setNotes(blankToNull(request.notes()));
        schedule.setTopic(topicOf(request));
        schedule.setStatus(newStatus);

        if (cancelled) {
            notifyStudents(schedule, "cancelled");
        } else if (timingChanged) {
            notifyStudents(schedule, "rescheduled");
        }
        auditService.record("ClassSchedule", id, cancelled ? AuditService.STATUS_CHANGE : AuditService.UPDATE,
                (cancelled ? "Cancelled " : "Updated ") + schedule.getSubject().getName() + " for "
                        + batch.getName() + " on " + schedule.getScheduleDate());
        return ScheduleResponse.from(schedule);
    }

    private void notifyStudents(ClassSchedule schedule, String change) {
        for (Student student : studentRepository.findActiveByBatchId(schedule.getBatch().getId())) {
            notificationService.publish(NotificationEvent.CLASS_SCHEDULE_CHANGED, student,
                    messageFactory.scheduleChanged(student.getFullName(), schedule.getSubject().getName(),
                            schedule.getScheduleDate(), schedule.getStartTime(), change));
        }
    }

    private List<Conflict> conflictsOn(LocalDate date, ScheduleRequest request, Long excludeId) {
        String room = blankToNull(request.room());
        List<ClassSchedule> candidates = repository.findConflicts(date, request.startTime(), request.endTime(),
                request.facultyId(), request.batchId(), room, excludeId);
        return ScheduleConflictChecker.find(date, request.startTime(), request.endTime(), request.facultyId(),
                request.batchId(), room, excludeId, candidates);
    }

    private List<LocalDate> occurrences(ScheduleRequest request) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate until = request.repeatWeeklyUntil() == null ? request.scheduleDate() : request.repeatWeeklyUntil();
        for (LocalDate date = request.scheduleDate(); !date.isAfter(until); date = date.plusWeeks(1)) {
            dates.add(date);
            if (dates.size() > MAX_OCCURRENCES) {
                throw new BusinessRuleException("A repeating schedule can create at most " + MAX_OCCURRENCES + " classes");
            }
        }
        return dates;
    }

    private com.coyotai.education.syllabus.SyllabusTopic topicOf(ScheduleRequest request) {
        if (request.topicId() == null) return null;
        var topic = topics.findById(request.topicId()).orElseThrow(() -> ResourceNotFoundException.of("Topic", request.topicId()));
        if (!topic.isActive() || !topic.getSubject().getId().equals(request.subjectId())) {
            throw new BusinessRuleException("Choose an active topic belonging to the selected subject");
        }
        return topic;
    }

    private void validate(ScheduleRequest request) {
        topicOf(request);
        if (!request.startTime().isBefore(request.endTime())) {
            throw new BusinessRuleException("The start time must be before the end time");
        }
        if (request.repeatWeeklyUntil() != null && request.repeatWeeklyUntil().isBefore(request.scheduleDate())) {
            throw new BusinessRuleException("\"Repeat until\" cannot be before the first class");
        }
    }

    private Subject subjectOf(Batch batch, Long subjectId) {
        Subject subject = courseService.getSubject(subjectId);
        if (!subject.getCourse().getId().equals(batch.getCourse().getId())) {
            throw new BusinessRuleException(subject.getName() + " is not taught in " + batch.getCourse().getName());
        }
        return subject;
    }

    private void requireRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessRuleException("The end date must not be before the start date");
        }
        if (from.plusDays(366).isBefore(to)) {
            throw new BusinessRuleException("Please choose a range of one year or less");
        }
    }

    public ClassSchedule get(Long id) {
        return repository.findDetail(id).orElseThrow(() -> ResourceNotFoundException.of("Class", id));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
