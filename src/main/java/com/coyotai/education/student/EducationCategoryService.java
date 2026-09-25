package com.coyotai.education.student;

import com.coyotai.education.audit.AuditService;
import com.coyotai.education.common.*;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class EducationCategoryService {
    private final EducationCategoryRepository repository;
    private final AuditService audit;
    public EducationCategoryService(EducationCategoryRepository repository, AuditService audit) {
        this.repository = repository; this.audit = audit;
    }
    public record Request(@NotBlank @Size(max=30) @Pattern(regexp="[A-Za-z0-9_]+", message="Use letters, numbers and underscores") String code,
                          @NotBlank @Size(max=100) String name, @Min(0) Integer displayOrder, Boolean active) {}
    public record Response(Long id, String code, String name, int displayOrder, boolean active) {
        public static Response from(EducationCategory c) {
            return c == null ? null : new Response(c.getId(), c.getCode(), c.getName(), c.getDisplayOrder(), c.isActive());
        }
    }
    @Transactional(readOnly=true)
    public List<Response> list() {
        return repository.findAllByOrderByDisplayOrderAscNameAsc().stream().map(Response::from).toList();
    }
    @Transactional
    public Response save(Long id, Request request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        EducationCategory category = id == null ? new EducationCategory() : get(id);
        if (id != null && !code.equals(category.getCode())) throw new BusinessRuleException("Category codes cannot be changed; edit the display name instead");
        if (id == null && repository.existsByCodeIgnoreCase(code)) throw new DuplicateResourceException("An education category with this code already exists");
        category.setCode(code); category.setName(request.name().trim());
        category.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        category.setActive(request.active() == null || request.active());
        repository.save(category);
        audit.record("EducationCategory", category.getId(), id == null ? AuditService.CREATE : AuditService.UPDATE, "Saved education category " + category.getName());
        return Response.from(category);
    }
    public EducationCategory get(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Education category", id));
    }
    public EducationCategory select(Long id, EducationCategory existing) {
        EducationCategory category = get(id);
        if (!category.isActive() && (existing == null || !Objects.equals(existing.getId(), id))) {
            throw new BusinessRuleException("Choose an active education category");
        }
        return category;
    }
    public EducationCategory byCode(String code) {
        return repository.findByCodeIgnoreCase(code).orElseThrow(() -> new BusinessRuleException("Configure education category " + code + " in Master data"));
    }
}
