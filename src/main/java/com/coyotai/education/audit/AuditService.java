package com.coyotai.education.audit;

import com.coyotai.education.security.AppUserDetails;
import com.coyotai.education.security.CurrentUser;
import com.coyotai.education.util.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Records important changes. Runs inside the caller's transaction, so an audit entry exists
 * exactly when the change it describes was committed.
 */
@Service
public class AuditService {

    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String STATUS_CHANGE = "STATUS_CHANGE";
    public static final String PUBLISH = "PUBLISH";
    public static final String DELETE = "DELETE";

    private static final int MAX_SUMMARY = 500;

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String entityType, Long entityId, String action, String summary) {
        record(entityType, entityId, action, summary, null);
    }

    @Transactional
    public void record(String entityType, Long entityId, String action, String summary, Object details) {
        AuditLog log = new AuditLog();
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setSummary(summary.length() > MAX_SUMMARY ? summary.substring(0, MAX_SUMMARY) : summary);
        log.setDetails(details == null ? null : JsonUtils.toJson(details));
        Optional<AppUserDetails> user = CurrentUser.get();
        log.setPerformedById(user.map(AppUserDetails::getId).orElse(null));
        log.setPerformedByName(user.map(AppUserDetails::getFullName).orElse("System"));
        log.setPerformedAt(Instant.now());
        repository.save(log);
    }
}
