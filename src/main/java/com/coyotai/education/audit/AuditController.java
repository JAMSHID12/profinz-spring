package com.coyotai.education.audit;

import com.coyotai.education.common.ApiResponse;
import com.coyotai.education.common.PageResponse;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.RequiresModule;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/audit-logs")
@RequiresModule(ModuleCode.ADMINISTRATION)
public class AuditController {

    private final AuditLogRepository repository;

    public AuditController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<Entry>> search(@RequestParam(required = false) String entityType,
                                                   @RequestParam(required = false) Long entityId,
                                                   @RequestParam(required = false) String action,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(PageResponse.of(
                repository.search(blankToNull(entityType), entityId, blankToNull(action),
                        PageRequest.of(page, Math.min(size, 200))),
                Entry::from));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    public record Entry(Long id, String entityType, Long entityId, String action, String summary,
                        String details, String performedByName, Instant performedAt) {

        static Entry from(AuditLog log) {
            return new Entry(log.getId(), log.getEntityType(), log.getEntityId(), log.getAction(),
                    log.getSummary(), log.getDetails(), log.getPerformedByName(), log.getPerformedAt());
        }
    }
}
