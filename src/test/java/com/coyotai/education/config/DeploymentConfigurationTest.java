package com.coyotai.education.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentConfigurationTest {
    @ParameterizedTest
    @ValueSource(strings = {"staging", "production"})
    void cloudProfilesUseExplicitSettingsAndNeverLoadLocalSecrets(String profile) {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profile,
                        "DB_HOST=mysql.test.internal", "DB_PORT=3306", "DB_NAME=test_database",
                        "DB_USERNAME=test_user", "DB_PASSWORD=test-only-password",
                        "JWT_SECRET=test-only-signing-secret-with-at-least-32-bytes",
                        "CORS_ALLOWED_ORIGINS=https://frontend.example.test",
                        "PROJECT_STUDENT_DEFAULT_PASSWORD=test-only-student-password", "PORT=9090")
                .run(context -> {
                    var env = context.getEnvironment();
                    assertThat(env.getActiveProfiles()).contains(profile, "cloud");
                    assertThat(env.getProperty("spring.datasource.url"))
                            .startsWith("jdbc:mysql://mysql.test.internal:3306/test_database?");
                    assertThat(env.getProperty("spring.datasource.password")).isEqualTo("test-only-password");
                    assertThat(env.getProperty("server.port")).isEqualTo("9090");
                    assertThat(env.getProperty("storage.student-photos.directory")).isEqualTo("/data/student-photos");
                    assertThat(env.getProperty("project.bootstrap.seed-sample-data")).isEqualTo("false");
                    assertThat(env.getProperty("spring.jpa.show-sql")).isEqualTo("false");
                    assertThat(env.getProperty("whatsapp.enabled")).isEqualTo("false");
                    assertThat(env.getProperty("whatsapp.queue.enabled")).isEqualTo("false");
                    assertThat(env.getProperty("spring.config.import")).isNull();
                    env.getPropertySources().forEach(source -> assertThat(source.getName()).doesNotContain(".env"));
                });
    }

    @Test void composeYamlParsesAndUsesTheSameAppBuildContexts() throws Exception {
        var source = new YamlPropertySourceLoader().load("compose", new FileSystemResource("../docker-compose.yml")).get(0);
        assertThat(source.getProperty("services.backend.build")).isEqualTo("./backend");
        assertThat(source.getProperty("services.frontend.build.context")).isEqualTo("./frontend");
        assertThat(source.getProperty("services.backend.environment.SPRING_PROFILES_ACTIVE")).isEqualTo("staging");
    }
}