package com.coyotai.education.schedule;

import com.coyotai.education.audit.AuditService;
import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.common.DuplicateResourceException;
import com.coyotai.education.common.ForbiddenException;
import com.coyotai.education.common.ResourceNotFoundException;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.schedule.ScheduleDtos.EntryExitRequest;
import com.coyotai.education.schedule.ScheduleDtos.EntryExitResponse;
import com.coyotai.education.schedule.ScheduleDtos.RegisterRequest;
import com.coyotai.education.schedule.ScheduleDtos.RegisterResponse;
import com.coyotai.education.security.AppUserDetails;
import com.coyotai.education.security.CurrentUser;
import com.coyotai.education.security.DataScope;
import com.coyotai.education.security.DataScopeService;
import com.coyotai.education.staff.Faculty;
import com.coyotai.education.staff.StaffService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Class register (what was taught) and faculty entry/exit. */
@Service
public class TeachingService {

    private final ClassRegisterEntryRepository registerRepository;
    private final FacultyEntryExitRepository entryExitRepository;
    private final ScheduleService scheduleService;
    private final StaffService staffService;
    private final DataScopeService dataScopeService;
    private final ProjectConfigService configService;
    private final AuditService auditService;

    public TeachingService(ClassRegisterEntryRepository registerRepository,
                           FacultyEntryExitRepository entryExitRepository, ScheduleService scheduleService,
                           StaffService staffService, DataScopeService dataScopeService,
                           ProjectConfigService configService, AuditService auditService) {
        this.registerRepository = registerRepository;
        this.entryExitRepository = entryExitRepository;
        this.scheduleService = scheduleService;
        this.staffService = staffService;
        this.dataScopeService = dataScopeService;
        this.configService = configService;
        this.auditService = auditService;
    }

    // ---- Class register -----------------------------------------------------

    @Transactional(readOnly = true)
    public List<RegisterResponse> register(LocalDate from, LocalDate to, boolean mine) {
        DataScope scope = dataScopeService.current();
        Long facultyId = mine ? CurrentUser.require().getFacultyId() : null;
        if (mine && facultyId == null) {
            return List.of();
        }
        return registerRepository.search(from, to, facultyId, scope.isGlobal(), scope.batchIdsForQuery())
                .stream().map(RegisterResponse::from).toList();
    }

    /** One entry per scheduled class; recording it again updates it. Marks the class completed. */
    @Transactional
    public RegisterResponse record(RegisterRequest request) {
        ClassSchedule schedule = scheduleService.get(request.scheduleId());
        AppUserDetails user = CurrentUser.require();
        DataScope scope = dataScopeService.current();
        boolean ownClass = schedule.getFaculty() != null && schedule.getFaculty().getId().equals(user.getFacultyId());
        if (!scope.isGlobal() && !ownClass && !scope.mentorBatchIds().contains(schedule.getBatch().getId())) {
            throw new ForbiddenException("You can only record the register for your own classes");
        }
        if (schedule.getStatus() == ClassSchedule.Status.CANCELLED) {
            throw new BusinessRuleException("This class was cancelled");
        }
        if (schedule.getScheduleDate().isAfter(configService.today())) {
            throw new BusinessRuleException("The register can only be recorded on or after the day of the class");
        }
        if (!request.actualStart().isBefore(request.actualEnd())) {
            throw new BusinessRuleException("The actual start must be before the actual end");
        }
        ClassRegisterEntry entry = registerRepository.findByClassScheduleId(schedule.getId()).orElseGet(ClassRegisterEntry::new);
        boolean created = entry.getId() == null;
        entry.setClassSchedule(schedule);
        entry.setFaculty(schedule.getFaculty());
        entry.setActualStart(request.actualStart());
        entry.setActualEnd(request.actualEnd());
        entry.setTopicCovered(request.topicCovered().trim());
        entry.setStudentCount(request.studentCount());
        entry.setRemarks(blankToNull(request.remarks()));
        registerRepository.save(entry);
        schedule.setStatus(ClassSchedule.Status.COMPLETED);
        auditService.record("ClassRegister", entry.getId(), created ? AuditService.CREATE : AuditService.UPDATE,
                "Class register for " + schedule.getSubject().getName() + " / " + schedule.getBatch().getName()
                        + " on " + schedule.getScheduleDate());
        return RegisterResponse.from(entry);
    }

    // ---- Entry / exit ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EntryExitResponse> entries(LocalDate from, LocalDate to, Long facultyId) {
        AppUserDetails user = CurrentUser.require();
        Long effective = dataScopeService.current().isGlobal() ? facultyId : user.getFacultyId();
        if (effective == null && !dataScopeService.current().isGlobal()) {
            return List.of();
        }
        return entryExitRepository.search(from, to, effective).stream().map(EntryExitResponse::from).toList();
    }

    /** Faculty record their own; staff with a global scope may record for anyone. */
    @Transactional
    public EntryExitResponse recordEntry(EntryExitRequest request) {
        Faculty faculty = resolveFaculty(request.facultyId());
        String session = request.sessionLabel().trim();
        if (entryExitRepository.existsByFacultyIdAndEntryDateAndSessionLabelIgnoreCase(faculty.getId(),
                request.entryDate(), session)) {
            throw new DuplicateResourceException(faculty.getFullName() + " already has an entry for the "
                    + session + " session on " + request.entryDate());
        }
        if (request.entryDate().isAfter(configService.today())) {
            throw new BusinessRuleException("Entry cannot be recorded for a future date");
        }
        validateTimes(request);
        FacultyEntryExit entry = new FacultyEntryExit();
        entry.setFaculty(faculty);
        entry.setEntryDate(request.entryDate());
        entry.setSessionLabel(session);
        entry.setEntryTime(request.entryTime());
        entry.setExitTime(request.exitTime());
        entry.setRemarks(blankToNull(request.remarks()));
        entryExitRepository.save(entry);
        auditService.record("FacultyEntryExit", entry.getId(), AuditService.CREATE,
                faculty.getFullName() + " entry on " + request.entryDate() + " (" + session + ")");
        return EntryExitResponse.from(entry);
    }

    @Transactional
    public EntryExitResponse updateEntry(Long id, EntryExitRequest request) {
        FacultyEntryExit entry = entryExitRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Entry", id));
        AppUserDetails user = CurrentUser.require();
        if (!dataScopeService.current().isGlobal() && !entry.getFaculty().getId().equals(user.getFacultyId())) {
            throw new ForbiddenException("You can only update your own entry and exit records");
        }
        validateTimes(request);
        entry.setEntryTime(request.entryTime());
        entry.setExitTime(request.exitTime());
        entry.setRemarks(blankToNull(request.remarks()));
        auditService.record("FacultyEntryExit", id, AuditService.UPDATE,
                entry.getFaculty().getFullName() + " entry/exit updated for " + entry.getEntryDate());
        return EntryExitResponse.from(entry);
    }

    private Faculty resolveFaculty(Long requestedFacultyId) {
        AppUserDetails user = CurrentUser.require();
        boolean global = dataScopeService.current().isGlobal();
        if (requestedFacultyId != null && global) {
            return staffService.getFaculty(requestedFacultyId);
        }
        if (user.getFacultyId() == null) {
            throw new BusinessRuleException("Choose the faculty member this entry belongs to");
        }
        if (requestedFacultyId != null && !requestedFacultyId.equals(user.getFacultyId())) {
            throw new ForbiddenException("You can only record your own entry and exit");
        }
        return staffService.getFaculty(user.getFacultyId());
    }

    private void validateTimes(EntryExitRequest request) {
        if (request.exitTime() != null && !request.entryTime().isBefore(request.exitTime())) {
            throw new BusinessRuleException("The exit time must be after the entry time");
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
