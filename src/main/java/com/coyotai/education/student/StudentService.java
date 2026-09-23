package com.coyotai.education.student;

import com.coyotai.education.academic.Batch;
import com.coyotai.education.academic.BatchService;
import com.coyotai.education.academic.CourseService;
import com.coyotai.education.auth.User;
import com.coyotai.education.auth.UserService;
import com.coyotai.education.audit.AuditService;
import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.common.ResourceNotFoundException;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.platform.RoleCode;
import com.coyotai.education.security.DataScope;
import com.coyotai.education.security.DataScopeService;
import com.coyotai.education.student.StudentDtos.BatchHistoryEntry;
import com.coyotai.education.student.StudentDtos.StudentDetail;
import com.coyotai.education.student.StudentDtos.StudentRequest;
import com.coyotai.education.student.StudentDtos.StudentResponse;
import com.coyotai.education.student.StudentDtos.TransferRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Student records. Staff see students through their data scope; changing a student's batch
 * closes the current assignment and opens a new one, so history is never overwritten.
 */
@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final StudentBatchAssignmentRepository assignmentRepository;
    private final BatchService batchService;
    private final CourseService courseService;
    private final StudentIdentifiers identifiers;
    private final StudentPhotoStorage photos;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final DataScopeService dataScopeService;
    private final ProjectConfigService configService;
    private final AuditService auditService;

    public StudentService(StudentRepository studentRepository, StudentBatchAssignmentRepository assignmentRepository,
                          BatchService batchService, CourseService courseService, StudentIdentifiers identifiers, StudentPhotoStorage photos,
                          UserService userService, PasswordEncoder passwordEncoder,
                          DataScopeService dataScopeService, ProjectConfigService configService,
                          AuditService auditService) {
        this.studentRepository = studentRepository;
        this.assignmentRepository = assignmentRepository;
        this.batchService = batchService;
        this.courseService = courseService;
        this.identifiers = identifiers;
        this.photos = photos;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.dataScopeService = dataScopeService;
        this.configService = configService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> search(String search, Long courseId, Long batchId, Student.Status status) {
        DataScope scope = dataScopeService.current();
        if (batchId != null) {
            scope.requireBatch(batchId);
        }
        String term = (search == null || search.isBlank()) ? null : search.trim();
        return studentRepository.search(term, courseId, batchId, status, scope.isGlobal(), scope.batchIdsForQuery())
                .stream().map(StudentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public StudentDetail detail(Long id) {
        Student student = getDetail(id);
        dataScopeService.requireStudent(student);
        return StudentDetail.from(student);
    }

    @Transactional(readOnly = true)
    public List<BatchHistoryEntry> batchHistory(Long id) {
        dataScopeService.requireStudent(getDetail(id));
        return assignmentRepository.findHistory(id).stream().map(BatchHistoryEntry::from).toList();
    }

    @Transactional
    public StudentDetail create(StudentRequest request) {
        if (request.batchId() == null) throw new BusinessRuleException("Select a batch before registering a student");
        Batch batch = batchService.getBatch(request.batchId());
        dataScopeService.current().requireBatch(batch.getId());
        String admission = identifiers.nextAdmission(admissionOrToday(request).getYear());
        String code = identifiers.nextStudentCode(batch);

        Student student = new Student();
        student.setAdmissionNumber(admission);
        student.setStudentCode(code);
        applyProfile(student, request);
        student.setAdmissionDate(admissionOrToday(request));
        student.setStatus(request.status() == null ? Student.Status.ACTIVE : request.status());

        placeInBatch(student, batch, request.courseId());
        studentRepository.save(student);
        if (batch != null) {
            openAssignment(student, batch, admissionOrToday(request), "Admission");
        }
        if (Boolean.TRUE.equals(request.createLogin())) {
            student.setUser(newStudentLogin(student, null));
        }
        auditService.record("Student", student.getId(), AuditService.CREATE,
                "Admitted " + student.getFullName() + " (" + admission + ")");
        return StudentDetail.from(student);
    }

    @Transactional
    public StudentDetail update(Long id, StudentRequest request) {
        Student student = getDetail(id);
        dataScopeService.requireStudent(student);
        applyProfile(student, request);

        Student.Status previousStatus = student.getStatus();
        student.setStatus(request.status() == null ? student.getStatus() : request.status());

        Long currentBatchId = student.getBatch() == null ? null : student.getBatch().getId();
        if (!Objects.equals(currentBatchId, request.batchId())) {
            transfer(student, request.batchId(), configService.today(), "Changed on student record");
        } else if (request.batchId() == null) {
            placeInBatch(student, null, request.courseId());
        }
        if (student.getUser() != null) {
            student.getUser().setFullName(student.getFullName());
            student.getUser().setActive(student.getStatus() == Student.Status.ACTIVE);
        }
        auditService.record("Student", student.getId(),
                previousStatus != student.getStatus() ? AuditService.STATUS_CHANGE : AuditService.UPDATE,
                "Updated " + student.getFullName()
                        + (previousStatus != student.getStatus() ? " (status " + previousStatus + " -> " + student.getStatus() + ")" : ""));
        return StudentDetail.from(student);
    }

    @Transactional
    public StudentDetail transfer(Long id, TransferRequest request) {
        Student student = getDetail(id);
        dataScopeService.requireStudent(student);
        LocalDate effective = request.effectiveDate() == null ? configService.today() : request.effectiveDate();
        transfer(student, request.batchId(), effective,
                request.reason() == null || request.reason().isBlank() ? "Batch transfer" : request.reason().trim());
        return StudentDetail.from(student);
    }

    /** Creates a login for the student, or resets the password of the existing one. */
    @Transactional
    public StudentDetail createOrResetLogin(Long id, String password) {
        Student student = getDetail(id);
        dataScopeService.requireStudent(student);
        if (student.getUser() == null) {
            student.setUser(newStudentLogin(student, password));
            auditService.record("Student", id, AuditService.UPDATE, "Created portal login for " + student.getFullName());
        } else {
            User user = student.getUser();
            user.setPassword(passwordEncoder.encode(password == null || password.isBlank()
                    ? configService.student().getDefaultPassword() : password));
            user.setMustChangePassword(true);
            user.setActive(true);
            auditService.record("Student", id, AuditService.UPDATE, "Reset portal password for " + student.getFullName());
        }
        return StudentDetail.from(student);
    }

    private void transfer(Student student, Long newBatchId, LocalDate effective, String reason) {
        LocalDate closeDate = effective.minusDays(1);
        assignmentRepository.findFirstByStudentIdAndStatus(student.getId(), StudentBatchAssignment.Status.CURRENT)
                .ifPresent(current -> {
                    current.setStatus(StudentBatchAssignment.Status.TRANSFERRED);
                    current.setEndDate(closeDate.isBefore(current.getStartDate()) ? current.getStartDate() : closeDate);
                    current.setReason(reason);
                });
        String from = student.getBatch() == null ? "no batch" : student.getBatch().getName();
        Batch batch = newBatchId == null ? null : batchService.getBatch(newBatchId);
        placeInBatch(student, batch, null);
        if (batch != null) {
            openAssignment(student, batch, effective, reason);
        }
        auditService.record("Student", student.getId(), AuditService.UPDATE,
                "Moved " + student.getFullName() + " from " + from + " to " + (batch == null ? "no batch" : batch.getName()));
    }

    private void openAssignment(Student student, Batch batch, LocalDate start, String reason) {
        StudentBatchAssignment assignment = new StudentBatchAssignment();
        assignment.setStudent(student);
        assignment.setBatch(batch);
        assignment.setCourse(batch.getCourse());
        assignment.setAcademicYear(batch.getAcademicYear());
        assignment.setStartDate(start);
        assignment.setStatus(StudentBatchAssignment.Status.CURRENT);
        assignment.setReason(reason);
        assignmentRepository.save(assignment);
    }

    /** Current placement: the batch decides course and year; without a batch the course may be set directly. */
    private void placeInBatch(Student student, Batch batch, Long courseId) {
        if (batch != null) {
            student.setBatch(batch);
            student.setCourse(batch.getCourse());
            student.setAcademicYear(batch.getAcademicYear());
        } else {
            student.setBatch(null);
            student.setCourse(courseId == null ? null : courseService.getCourse(courseId));
        }
    }

    private void applyProfile(Student student, StudentRequest request) {
        student.setFullName(request.fullName().trim());
        student.setDateOfBirth(request.dateOfBirth());
        student.setGender(blankToNull(request.gender()));
        student.setMobile(blankToNull(request.mobile()));
        student.setEmail(blankToNull(request.email()));
        student.setAddress(blankToNull(request.address()));
        student.setAdmissionDate(request.admissionDate());
        ParentContact parent = student.getParent() == null ? new ParentContact() : student.getParent();
        parent.setName(request.parentName().trim());
        String phone = request.parentPhoneNumber().trim();
        // A changed contact number is also the new WhatsApp destination.
        if (!phone.equals(parent.getPhoneNumber())) parent.setWhatsappNumber(null);
        parent.setPhoneNumber(phone);
        if (request.parentWhatsappOptIn() != null) parent.setWhatsappOptIn(request.parentWhatsappOptIn());
        student.setParent(parent);
        if (student.getDateOfBirth() != null && student.getDateOfBirth().isAfter(configService.today())) {
            throw new BusinessRuleException("Date of birth cannot be in the future");
        }
    }

    private User newStudentLogin(Student student, String password) {
        String raw = (password == null || password.isBlank()) ? configService.student().getDefaultPassword() : password;
        return userService.createAccount(student.getAdmissionNumber(), raw, student.getFullName(),
                student.getEmail(), student.getMobile(), Set.of(RoleCode.STUDENTS), true);
    }

    private LocalDate admissionOrToday(StudentRequest request) {
        return request.admissionDate() == null ? configService.today() : request.admissionDate();
    }

    @Transactional
    public StudentDetail saveWithPhoto(Long id, StudentRequest request, org.springframework.web.multipart.MultipartFile photo) {
        // Validate before writing either the student or the file.
        byte[] image = photo == null ? null : photos.validate(photo);
        StudentDetail result = id == null ? create(request) : update(id, request);
        Student student = getDetail(result.id());
        if (image != null) {
            String previous = student.getPhotoFile();
            student.setPhotoFile(photos.store(image, previous));
            student.setPhotoUrl("/api/students/" + student.getId() + "/photo?v=" + student.getPhotoFile());
        }
        return StudentDetail.from(student);
    }

    @Transactional(readOnly = true)
    public org.springframework.core.io.Resource photo(Long id) {
        Student student = getDetail(id);
        dataScopeService.requireStudent(student);
        return photos.load(student.getPhotoFile());
    }

    public Student getDetail(Long id) {
        return studentRepository.findDetail(id).orElseThrow(() -> ResourceNotFoundException.of("Student", id));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
