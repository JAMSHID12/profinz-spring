package com.coyotai.education.staff;

import com.coyotai.education.academic.Batch;
import com.coyotai.education.academic.BatchService;
import com.coyotai.education.academic.CourseService;
import com.coyotai.education.academic.Subject;
import com.coyotai.education.auth.User;
import com.coyotai.education.auth.UserService;
import com.coyotai.education.audit.AuditService;
import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.common.DuplicateResourceException;
import com.coyotai.education.common.ResourceNotFoundException;
import com.coyotai.education.platform.RoleCode;
import com.coyotai.education.staff.StaffDtos.AssignmentRequest;
import com.coyotai.education.staff.StaffDtos.AssignmentResponse;
import com.coyotai.education.staff.StaffDtos.FacultyResponse;
import com.coyotai.education.staff.StaffDtos.MentorResponse;
import com.coyotai.education.staff.StaffDtos.StaffRequest;
import com.coyotai.education.academic.BatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Mentors, faculty and which faculty member teaches what to whom. */
@Service
public class StaffService {

    private final MentorRepository mentorRepository;
    private final FacultyRepository facultyRepository;
    private final FacultyAssignmentRepository assignmentRepository;
    private final BatchRepository batchRepository;
    private final BatchService batchService;
    private final CourseService courseService;
    private final UserService userService;
    private final AuditService auditService;

    public StaffService(MentorRepository mentorRepository, FacultyRepository facultyRepository,
                        FacultyAssignmentRepository assignmentRepository, BatchRepository batchRepository,
                        BatchService batchService, CourseService courseService, UserService userService,
                        AuditService auditService) {
        this.mentorRepository = mentorRepository;
        this.facultyRepository = facultyRepository;
        this.assignmentRepository = assignmentRepository;
        this.batchRepository = batchRepository;
        this.batchService = batchService;
        this.courseService = courseService;
        this.userService = userService;
        this.auditService = auditService;
    }

    // ---- Mentors ------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MentorResponse> mentors() {
        Map<Long, Long> batchCounts = batchRepository.findAll().stream()
                .filter(batch -> batch.getMentor() != null)
                .collect(Collectors.groupingBy(batch -> batch.getMentor().getId(), Collectors.counting()));
        return mentorRepository.findAllWithUser().stream()
                .map(mentor -> MentorResponse.from(mentor, batchCounts.getOrDefault(mentor.getId(), 0L)))
                .toList();
    }

    @Transactional
    public MentorResponse createMentor(StaffRequest request) {
        requireUniqueEmployeeCode(request.employeeCode(), mentorRepository.existsByEmployeeCodeIgnoreCase(
                request.employeeCode() == null ? "" : request.employeeCode().trim()));
        Mentor mentor = new Mentor();
        applyMentor(mentor, request);
        mentor.setUser(createLogin(request, RoleCode.MENTORS));
        mentorRepository.save(mentor);
        auditService.record("Mentor", mentor.getId(), AuditService.CREATE, "Created mentor " + mentor.getFullName());
        return MentorResponse.from(mentor, 0);
    }

    @Transactional
    public MentorResponse updateMentor(Long id, StaffRequest request) {
        Mentor mentor = mentorRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Mentor", id));
        String code = request.employeeCode() == null ? null : request.employeeCode().trim();
        if (code != null && !code.isEmpty() && !code.equalsIgnoreCase(mentor.getEmployeeCode())
                && mentorRepository.existsByEmployeeCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Employee code is already in use: " + code);
        }
        applyMentor(mentor, request);
        syncUser(mentor.getUser(), mentor.getFullName(), mentor.getEmail(), mentor.getMobile(), mentor.isActive());
        auditService.record("Mentor", mentor.getId(), AuditService.UPDATE, "Updated mentor " + mentor.getFullName());
        return MentorResponse.from(mentor, batchRepository.findIdsByMentorId(id).size());
    }

    private void applyMentor(Mentor mentor, StaffRequest request) {
        mentor.setFullName(request.fullName().trim());
        mentor.setEmployeeCode(blankToNull(request.employeeCode()));
        mentor.setMobile(blankToNull(request.mobile()));
        mentor.setEmail(blankToNull(request.email()));
        mentor.setSpecialization(blankToNull(request.specialization()));
        mentor.setActive(request.active() == null || request.active());
    }

    // ---- Faculty ------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<FacultyResponse> faculty() {
        return facultyRepository.findAllWithUser().stream().map(FacultyResponse::from).toList();
    }

    @Transactional
    public FacultyResponse createFaculty(StaffRequest request) {
        requireUniqueEmployeeCode(request.employeeCode(), facultyRepository.existsByEmployeeCodeIgnoreCase(
                request.employeeCode() == null ? "" : request.employeeCode().trim()));
        Faculty faculty = new Faculty();
        applyFaculty(faculty, request);
        faculty.setUser(createLogin(request, RoleCode.FACULTY));
        facultyRepository.save(faculty);
        auditService.record("Faculty", faculty.getId(), AuditService.CREATE, "Created faculty " + faculty.getFullName());
        return FacultyResponse.from(faculty);
    }

    @Transactional
    public FacultyResponse updateFaculty(Long id, StaffRequest request) {
        Faculty faculty = getFaculty(id);
        String code = request.employeeCode() == null ? null : request.employeeCode().trim();
        if (code != null && !code.isEmpty() && !code.equalsIgnoreCase(faculty.getEmployeeCode())
                && facultyRepository.existsByEmployeeCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Employee code is already in use: " + code);
        }
        applyFaculty(faculty, request);
        syncUser(faculty.getUser(), faculty.getFullName(), faculty.getEmail(), faculty.getMobile(), faculty.isActive());
        auditService.record("Faculty", faculty.getId(), AuditService.UPDATE, "Updated faculty " + faculty.getFullName());
        return FacultyResponse.from(faculty);
    }

    private void applyFaculty(Faculty faculty, StaffRequest request) {
        faculty.setFullName(request.fullName().trim());
        faculty.setEmployeeCode(blankToNull(request.employeeCode()));
        faculty.setMobile(blankToNull(request.mobile()));
        faculty.setEmail(blankToNull(request.email()));
        faculty.setSpecialization(blankToNull(request.specialization()));
        faculty.setFacultyType(request.facultyType() == null ? Faculty.Type.FULL_TIME : request.facultyType());
        faculty.setActive(request.active() == null || request.active());
    }

    public Faculty getFaculty(Long id) {
        return facultyRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Faculty", id));
    }

    // ---- Assignments --------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AssignmentResponse> assignments(Long facultyId, Long batchId) {
        return assignmentRepository.search(facultyId, batchId).stream().map(AssignmentResponse::from).toList();
    }

    @Transactional
    public AssignmentResponse assign(AssignmentRequest request) {
        Faculty faculty = getFaculty(request.facultyId());
        Batch batch = batchService.getBatch(request.batchId());
        Subject subject = courseService.getSubject(request.subjectId());
        if (!subject.getCourse().getId().equals(batch.getCourse().getId())) {
            throw new BusinessRuleException(subject.getName() + " is not a subject of " + batch.getCourse().getName());
        }
        if (assignmentRepository.existsByFacultyIdAndBatchIdAndSubjectId(faculty.getId(), batch.getId(), subject.getId())) {
            throw new DuplicateResourceException(faculty.getFullName() + " already teaches " + subject.getName()
                    + " to " + batch.getName());
        }
        FacultyAssignment assignment = new FacultyAssignment();
        assignment.setFaculty(faculty);
        assignment.setBatch(batch);
        assignment.setSubject(subject);
        assignment.setActive(true);
        assignmentRepository.save(assignment);
        auditService.record("FacultyAssignment", assignment.getId(), AuditService.CREATE,
                "Assigned " + faculty.getFullName() + " to " + subject.getName() + " for " + batch.getName());
        return AssignmentResponse.from(assignment);
    }

    @Transactional
    public AssignmentResponse setAssignmentActive(Long id, boolean active) {
        FacultyAssignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Assignment", id));
        assignment.setActive(active);
        auditService.record("FacultyAssignment", id, AuditService.STATUS_CHANGE,
                (active ? "Activated" : "Deactivated") + " teaching assignment " + id);
        return AssignmentResponse.from(assignment);
    }

    // ---- Helpers ------------------------------------------------------------

    private User createLogin(StaffRequest request, RoleCode role) {
        if (request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()) {
            throw new BusinessRuleException("A username and password are required to create a login");
        }
        return userService.createAccount(request.username(), request.password(), request.fullName(),
                request.email(), request.mobile(), Set.of(role), true);
    }

    private void syncUser(User user, String fullName, String email, String mobile, boolean active) {
        if (user == null) {
            return;
        }
        user.setFullName(fullName);
        user.setEmail(email);
        user.setMobile(mobile);
        user.setActive(active);
    }

    private void requireUniqueEmployeeCode(String code, boolean exists) {
        if (code != null && !code.isBlank() && exists) {
            throw new DuplicateResourceException("Employee code is already in use: " + code.trim());
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
