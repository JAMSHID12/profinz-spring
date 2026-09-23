package com.coyotai.education.tenant;

import com.coyotai.education.common.TenantEntity;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * Base class of every repository (see {@code JpaConfig}).
 *
 * <p>Hibernate restricts queries on {@code @TenantId} entities to the current client, but a
 * primary-key load - which is what {@code findById} does - bypasses that restriction. Here a
 * record that belongs to another client is reported as absent, exactly like an unknown id, so
 * guessing ids never reveals or exposes another client's data.
 */
public class TenantAwareRepository<T, ID> extends SimpleJpaRepository<T, ID> {

    private final EntityManager entityManager;

    public TenantAwareRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
    }

    @Override
    public Optional<T> findById(ID id) {
        return super.findById(id).filter(this::belongsToCurrentClient);
    }

    private boolean belongsToCurrentClient(T entity) {
        if (!(entity instanceof TenantEntity tenantEntity)) {
            return true;
        }
        // Repository methods always run in a transaction, so this is the session that loaded the entity.
        Object currentClient = entityManager.unwrap(Session.class).getTenantIdentifierValue();
        return Objects.equals(tenantEntity.getClientId(), currentClient);
    }
}
