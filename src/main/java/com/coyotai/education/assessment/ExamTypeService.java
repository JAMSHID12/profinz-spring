package com.coyotai.education.assessment;

import com.coyotai.education.assessment.AssessmentDtos.ExamTypeRequest;
import com.coyotai.education.assessment.AssessmentDtos.ExamTypeResponse;
import com.coyotai.education.audit.AuditService;
import com.coyotai.education.common.DuplicateResourceException;
import com.coyotai.education.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Exam types are master data: class exam, model exam, course exam, or whatever a client uses. */
@Service
public class ExamTypeService {

    private final ExamTypeRepository repository;
    private final AuditService auditService;

    public ExamTypeService(ExamTypeRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ExamTypeResponse> types() {
        return repository.findAllByOrderByDisplayOrderAscNameAsc().stream().map(ExamTypeResponse::from).toList();
    }

    @Transactional
    public ExamTypeResponse save(Long id, ExamTypeRequest request) {
        String code = request.code().trim().toUpperCase().replace(' ', '_');
        ExamType type = id == null ? new ExamType()
                : repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Exam type", id));
        if ((type.getId() == null || !code.equalsIgnoreCase(type.getCode())) && repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("An exam type with code " + code + " already exists");
        }
        type.setCode(code);
        type.setName(request.name().trim());
        type.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        type.setActive(request.active() == null || request.active());
        repository.save(type);
        auditService.record("ExamType", type.getId(), id == null ? AuditService.CREATE : AuditService.UPDATE,
                (id == null ? "Created" : "Updated") + " exam type " + type.getName());
        return ExamTypeResponse.from(type);
    }
}
