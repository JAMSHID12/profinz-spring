package com.coyotai.education.tenant;

import com.coyotai.education.platform.ProjectProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Resolves the database id of the configured client ({@code project.client.code}), creating
 * the row on first use. Plain JDBC on purpose: it is called while Hibernate is opening a
 * session, so it must not itself need a Hibernate session.
 */
@Component
public class ClientRegistry {

    private static final Logger log = LoggerFactory.getLogger(ClientRegistry.class);

    private final JdbcTemplate jdbcTemplate;
    private final ProjectProperties properties;
    private volatile Long configuredClientId;

    public ClientRegistry(JdbcTemplate jdbcTemplate, ProjectProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public Long configuredClientId() {
        Long id = configuredClientId;
        if (id == null) {
            synchronized (this) {
                if (configuredClientId == null) {
                    configuredClientId = findOrCreate(properties.getClient().getCode(),
                            properties.getClient().getName());
                }
                id = configuredClientId;
            }
        }
        return id;
    }

    /** Returns the id of the client with this code, creating it if needed. */
    public Long findOrCreate(String code, String name) {
        if (code == null || code.isBlank()) {
            throw new IllegalStateException("project.client.code must be configured");
        }
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM clients WHERE code = ?", Long.class, code);
        if (!ids.isEmpty()) {
            jdbcTemplate.update("UPDATE clients SET name = ?, updated_at = ? WHERE id = ? AND name <> ?",
                    displayName(code, name), now(), ids.get(0), displayName(code, name));
            return ids.get(0);
        }
        Timestamp now = now();
        jdbcTemplate.update("INSERT INTO clients (code, name, active, created_at, updated_at) VALUES (?, ?, TRUE, ?, ?)",
                code, displayName(code, name), now, now);
        Long id = jdbcTemplate.queryForObject("SELECT id FROM clients WHERE code = ?", Long.class, code);
        log.info("Registered client {} with id {}", code, id);
        return id;
    }

    private String displayName(String code, String name) {
        return (name == null || name.isBlank()) ? code : name;
    }

    private Timestamp now() {
        return Timestamp.from(Instant.now());
    }
}
