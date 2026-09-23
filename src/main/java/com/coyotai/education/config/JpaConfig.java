package com.coyotai.education.config;

import com.coyotai.education.security.CurrentUser;
import com.coyotai.education.tenant.TenantAwareRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Optional;

/**
 * Repositories use {@link TenantAwareRepository} so loading by id respects the client
 * boundary too. Auditing fills created_by / updated_by from the signed-in user (empty for
 * system work).
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.coyotai.education", repositoryBaseClass = TenantAwareRepository.class)
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaConfig {

    @Bean
    public AuditorAware<Long> auditorAware() {
        return () -> Optional.ofNullable(CurrentUser.idOrNull());
    }
}
