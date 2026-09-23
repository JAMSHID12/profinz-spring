package com.coyotai.education.support;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

/**
 * Chooses the MySQL instance for integration tests, once per test run:
 * <ol>
 *   <li>{@code IT_DB_URL} (+ {@code IT_DB_USERNAME}, {@code IT_DB_PASSWORD}) - an existing MySQL</li>
 *   <li>otherwise a Testcontainers {@code mysql:8.4} container when Docker is available</li>
 *   <li>otherwise none - integration tests are then skipped, never silently faked</li>
 * </ol>
 * The chosen database is wiped and re-migrated by Flyway at the start of the run.
 */
public final class TestDatabase {

    private static TestDatabase instance;

    private final String url;
    private final String username;
    private final String password;

    private TestDatabase(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public static synchronized TestDatabase get() {
        if (instance == null) {
            instance = resolve();
        }
        return instance;
    }

    public static boolean available() {
        return get() != null;
    }

    private static TestDatabase resolve() {
        String url = setting("IT_DB_URL");
        if (url != null) {
            return new TestDatabase(url, orDefault(setting("IT_DB_USERNAME"), "education"),
                    orDefault(setting("IT_DB_PASSWORD"), ""));
        }
        if (dockerAvailable()) {
            MySQLContainer<?> container = new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("education_platform_test")
                    .withUsername("education")
                    .withPassword("education_test_password");
            container.start();
            return new TestDatabase(container.getJdbcUrl() + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",
                    container.getUsername(), container.getPassword());
        }
        return null;
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String setting(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            value = System.getenv(name);
        }
        return value == null || value.isBlank() ? null : value;
    }

    private static String orDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }

    public String url() {
        return url;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }
}
