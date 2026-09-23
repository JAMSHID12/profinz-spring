package com.coyotai.education.academic;

import com.coyotai.education.academic.AcademicDtos.BatchRequest;
import com.coyotai.education.academic.AcademicDtos.BatchResponse;
import com.coyotai.education.audit.AuditService;
import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.common.DuplicateResourceException;
import com.coyotai.education.common.ResourceNotFoundException;
import com.coyotai.education.security.DataScope;
import com.coyotai.education.security.DataScopeService;
import com.coyotai.education.staff.Mentor;
import com.coyotai.education.staff.MentorRepository;
import com.coyotai.education.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Batches. Mentors and faculty only see their own batches (data scope). */
@Service
public class BatchService {

    private final BatchRepository batchRepository;
    private final CourseService courseService;
    private final AcademicYearService academicYearService;
    private final MentorRepository mentorRepository;
    private final StudentRepository studentRepository;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;

    public BatchService(BatchRepository batchRepository, CourseService courseService,
                        AcademicYearService academicYearService, MentorRepository mentorRepository,
                        StudentRepository studentRepository, DataScopeService dataScopeService,
                        AuditService auditService) {
        this.batchRepository = batchRepository;
        this.courseService = courseService;
        this.academicYearService = academicYearService;
        this.mentorRepository = mentorRepository;
        this.studentRepository = studentRepository;
        this.dataScopeService = dataScopeService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> search(Long courseId, Long academicYearId, Batch.Status status) {
        DataScope scope = dataScopeService.current();
        Map<Long, Long> counts = studentCounts();
        return batchRepository.search(courseId, academicYearId, status, scope.isGlobal(), scope.batchIdsForQuery())
                .stream()
                .map(batch -> BatchResponse.from(batch, counts.getOrDefault(batch.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public BatchResponse findById(Long id) {
        dataScopeService.current().requireBatch(id);
        Batch batch = batchRepository.findDetail(id).orElseThrow(() -> ResourceNotFoundException.of("Batch", id));
        return BatchResponse.from(batch, studentCounts().getOrDefault(id, 0L));
    }

    @Transactional
    public BatchResponse create(BatchRequest request) {
        validateDates(request);
        if (batchRepository.existsByAcademicYearIdAndNameIgnoreCase(request.academicYearId(), request.name().trim())) {
            throw new DuplicateResourceException("A batch named " + request.name().trim()
                    + " already exists in that academic year");
        }
        Batch batch = new Batch();
        apply(batch, request);
        batchRepository.save(batch);
        auditService.record("Batch", batch.getId(), AuditService.CREATE, "Created batch " + batch.getName());
        return BatchResponse.from(batch, 0);
    }

    @Transactional
    public BatchResponse update(Long id, BatchRequest request) {
        validateDates(request);
        Batch batch = getBatch(id);
        boolean renamed = !batch.getName().equalsIgnoreCase(request.name().trim())
                || !batch.getAcademicYear().getId().equals(request.academicYearId());
        if (renamed && batchRepository.existsByAcademicYearIdAndNameIgnoreCase(request.academicYearId(), request.name().trim())) {
            throw new DuplicateResourceException("A batch named " + request.name().trim()
                    + " already exists in that academic year");
        }
        Long previousMentor = batch.getMentor() == null ? null : batch.getMentor().getId();
        apply(batch, request);
        Long newMentor = batch.getMentor() == null ? null : batch.getMentor().getId();
        String summary = "Updated batch " + batch.getName()
                + (java.util.Objects.equals(previousMentor, newMentor) ? "" : " (mentor changed)");
        auditService.record("Batch", batch.getId(), AuditService.UPDATE, summary);
        return BatchResponse.from(batch, studentCounts().getOrDefault(id, 0L));
    }

    private void validateDates(BatchRequest request) {
        if (request.startDate() != null && request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new BusinessRuleException("The batch end date cannot be before its start date");
        }
    }

    private void apply(Batch batch, BatchRequest request) {
        batch.setName(request.name().trim());
        batch.setCourse(courseService.getCourse(request.courseId()));
        batch.setAcademicYear(academicYearService.get(request.academicYearId()));
        if (request.mentorId() == null) {
            batch.setMentor(null);
        } else {
            Mentor mentor = mentorRepository.findById(request.mentorId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Mentor", request.mentorId()));
            batch.setMentor(mentor);
        }
        batch.setStartDate(request.startDate());
        batch.setEndDate(request.endDate());
        batch.setCapacity(request.capacity());
        batch.setStatus(request.status() == null ? Batch.Status.ACTIVE : request.status());
        batch.setDescription(CourseService.trimToNull(request.description()));
    }

    public Batch getBatch(Long id) {
        return batchRepository.findDetail(id).orElseThrow(() -> ResourceNotFoundException.of("Batch", id));
    }

    private Map<Long, Long> studentCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : studentRepository.countActiveByBatch()) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }
}
