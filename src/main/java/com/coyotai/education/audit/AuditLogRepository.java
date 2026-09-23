package com.coyotai.education.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            select a from AuditLog a
            where (:entityType is null or a.entityType = :entityType)
              and (:entityId is null or a.entityId = :entityId)
              and (:action is null or a.action = :action)
            order by a.performedAt desc, a.id desc
            """)
    Page<AuditLog> search(@Param("entityType") String entityType,
                          @Param("entityId") Long entityId,
                          @Param("action") String action,
                          Pageable pageable);
}
