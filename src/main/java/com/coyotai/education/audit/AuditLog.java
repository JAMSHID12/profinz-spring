package com.coyotai.education.audit;

import com.coyotai.education.common.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Who changed what and when, for the operations the specification requires to be audited. */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog extends TenantEntity {

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "performed_by_id")
    private Long performedById;

    @Column(name = "performed_by_name", length = 150)
    private String performedByName;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;
}
