package com.coyotai.education.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tells Hibernate which client the current session belongs to. Every entity with a
 * {@code @TenantId} column is then written and filtered with this id automatically.
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {

    private final ClientRegistry clientRegistry;

    public TenantIdentifierResolver(ClientRegistry clientRegistry) {
        this.clientRegistry = clientRegistry;
    }

    @Override
    public Long resolveCurrentTenantIdentifier() {
        Long override = TenantContext.get();
        return override != null ? override : clientRegistry.configuredClientId();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
