package com.coyotai.education.academic;

import com.coyotai.education.academic.AcademicDtos.CourseRequest;
import com.coyotai.education.academic.AcademicDtos.CourseResponse;
import com.coyotai.education.academic.AcademicDtos.SubjectRequest;
import com.coyotai.education.academic.AcademicDtos.SubjectResponse;
import com.coyotai.education.audit.AuditService;
import com.coyotai.education.common.DuplicateResourceException;
import com.coyotai.education.common.RecordStatus;
import com.coyotai.education.common.ResourceNotFoundException;
import com.coyotai.education.util.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Courses and their subjects: master data, configurable per client. */
@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final SubjectRepository subjectRepository;
    private final AuditService auditService;

    public CourseService(CourseRepository courseRepository, SubjectRepository subjectRepository,
                         AuditService auditService) {
        this.courseRepository = courseRepository;
        this.subjectRepository = subjectRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> courses(boolean activeOnly) {
        List<Course> courses = activeOnly
                ? courseRepository.findAllByStatusOrderByDisplayOrderAscNameAsc(RecordStatus.ACTIVE)
                : courseRepository.findAllByOrderByDisplayOrderAscNameAsc();
        Map<Long, Long> subjectCounts = subjectRepository.search(null).stream()
                .collect(Collectors.groupingBy(subject -> subject.getCourse().getId(), Collectors.counting()));
        return courses.stream()
                .map(course -> CourseResponse.from(course, subjectCounts.getOrDefault(course.getId(), 0L)))
                .toList();
    }

    @Transactional
    public CourseResponse createCourse(CourseRequest request) {
        String code = request.code().trim().toUpperCase();
        if (courseRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("A course with code " + code + " already exists");
        }
        Course course = new Course();
        course.setCode(code);
        applyCourse(course, request);
        courseRepository.save(course);
        auditService.record("Course", course.getId(), AuditService.CREATE, "Created course " + course.getName());
        return CourseResponse.from(course, 0);
    }

    @Transactional
    public CourseResponse updateCourse(Long id, CourseRequest request) {
        Course course = getCourse(id);
        String code = request.code().trim().toUpperCase();
        if (!course.getCode().equalsIgnoreCase(code) && courseRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("A course with code " + code + " already exists");
        }
        BigDecimal previousFee = course.getFeeAmount();
        course.setCode(code);
        applyCourse(course, request);
        String feeChange = sameAmount(previousFee, course.getFeeAmount()) ? ""
                : " (course fee " + amountText(previousFee) + " -> " + amountText(course.getFeeAmount())
                        + ", applies to new fee plans)";
        auditService.record("Course", course.getId(), AuditService.UPDATE, "Updated course " + course.getName() + feeChange);
        return CourseResponse.from(course, subjectRepository.search(id).size());
    }

    private void applyCourse(Course course, CourseRequest request) {
        course.setName(request.name().trim());
        course.setDescription(trimToNull(request.description()));
        course.setStatus(request.status() == null ? RecordStatus.ACTIVE : request.status());
        course.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        course.setFeeAmount(request.feeAmount() == null ? null : Money.scale(request.feeAmount()));
        course.setDefaultInstallments(request.defaultInstallments());
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static String amountText(BigDecimal amount) {
        return amount == null ? "not set" : amount.toPlainString();
    }

    @Transactional(readOnly = true)
    public List<SubjectResponse> subjects(Long courseId) {
        return subjectRepository.search(courseId).stream().map(SubjectResponse::from).toList();
    }

    @Transactional
    public SubjectResponse createSubject(SubjectRequest request) {
        Course course = getCourse(request.courseId());
        String code = request.code().trim().toUpperCase();
        if (subjectRepository.existsByCourseIdAndCodeIgnoreCase(course.getId(), code)) {
            throw new DuplicateResourceException("Course " + course.getName() + " already has subject code " + code);
        }
        Subject subject = new Subject();
        subject.setCourse(course);
        subject.setCode(code);
        applySubject(subject, request);
        subjectRepository.save(subject);
        auditService.record("Subject", subject.getId(), AuditService.CREATE,
                "Created subject " + subject.getName() + " in " + course.getName());
        return SubjectResponse.from(subject);
    }

    @Transactional
    public SubjectResponse updateSubject(Long id, SubjectRequest request) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Subject", id));
        Course course = getCourse(request.courseId());
        String code = request.code().trim().toUpperCase();
        boolean identityChanged = !subject.getCode().equalsIgnoreCase(code)
                || !subject.getCourse().getId().equals(course.getId());
        if (identityChanged && subjectRepository.existsByCourseIdAndCodeIgnoreCase(course.getId(), code)) {
            throw new DuplicateResourceException("Course " + course.getName() + " already has subject code " + code);
        }
        subject.setCourse(course);
        subject.setCode(code);
        applySubject(subject, request);
        auditService.record("Subject", subject.getId(), AuditService.UPDATE, "Updated subject " + subject.getName());
        return SubjectResponse.from(subject);
    }

    private void applySubject(Subject subject, SubjectRequest request) {
        subject.setName(request.name().trim());
        subject.setDescription(trimToNull(request.description()));
        subject.setStatus(request.status() == null ? RecordStatus.ACTIVE : request.status());
        subject.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
    }

    public Course getCourse(Long id) {
        return courseRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Course", id));
    }

    public Subject getSubject(Long id) {
        return subjectRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Subject", id));
    }

    public Map<Long, Subject> subjectsById(List<Long> ids) {
        return subjectRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Subject::getId, Function.identity()));
    }

    static String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
