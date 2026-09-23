package com.coyotai.education.academic;

import com.coyotai.education.academic.AcademicDtos.AcademicYearRequest;
import com.coyotai.education.academic.AcademicDtos.AcademicYearResponse;
import com.coyotai.education.audit.AuditService;
import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.common.DuplicateResourceException;
import com.coyotai.education.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Academic years. Only one is "current"; adding a year never alters earlier ones. */
@Service
public class AcademicYearService {

    private final AcademicYearRepository repository;
    private final AuditService auditService;

    public AcademicYearService(AcademicYearRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<AcademicYearResponse> findAll() {
        return repository.findAllByOrderByStartDateDesc().stream().map(AcademicYearResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Optional<AcademicYear> current() {
        return repository.findFirstByCurrentTrue();
    }

    @Transactional
    public AcademicYearResponse create(AcademicYearRequest request) {
        validate(request);
        if (repository.existsByNameIgnoreCase(request.name().trim())) {
            throw new DuplicateResourceException("Academic year " + request.name() + " already exists");
        }
        AcademicYear year = new AcademicYear();
        apply(year, request);
        repository.save(year);
        if (year.isCurrent()) {
            repository.clearCurrentExcept(year.getId());
        }
        auditService.record("AcademicYear", year.getId(), AuditService.CREATE, "Created academic year " + year.getName());
        return AcademicYearResponse.from(year);
    }

    @Transactional
    public AcademicYearResponse update(Long id, AcademicYearRequest request) {
        validate(request);
        AcademicYear year = repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Academic year", id));
        if (!year.getName().equalsIgnoreCase(request.name().trim()) && repository.existsByNameIgnoreCase(request.name().trim())) {
            throw new DuplicateResourceException("Academic year " + request.name() + " already exists");
        }
        apply(year, request);
        repository.save(year);
        if (year.isCurrent()) {
            repository.clearCurrentExcept(year.getId());
        }
        auditService.record("AcademicYear", year.getId(), AuditService.UPDATE, "Updated academic year " + year.getName());
        return AcademicYearResponse.from(year);
    }

    private void validate(AcademicYearRequest request) {
        if (!request.startDate().isBefore(request.endDate())) {
            throw new BusinessRuleException("The start date must be before the end date");
        }
    }

    private void apply(AcademicYear year, AcademicYearRequest request) {
        year.setName(request.name().trim());
        year.setStartDate(request.startDate());
        year.setEndDate(request.endDate());
        year.setCurrent(Boolean.TRUE.equals(request.current()));
        year.setStatus(request.status() == null ? AcademicYear.Status.PLANNED : request.status());
    }

    public AcademicYear get(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Academic year", id));
    }
}
