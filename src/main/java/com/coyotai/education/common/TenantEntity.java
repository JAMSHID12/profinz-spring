package com.coyotai.education.common;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * Base class for every client-owned record.
 *
 * <p>{@code clientId} is a Hibernate {@link TenantId}: it is filled in automatically on insert
 * and every query is automatically restricted to the current client. Loading by id goes
 * through {@link com.coyotai.education.tenant.TenantAwareRepository}, which applies the same boundary.
 * Code never sets or filters it by hand, which is what makes "client A can never read
 * client B" hold everywhere.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "client_id", nullable = false, updatable = false)
    private Long clientId;
}
