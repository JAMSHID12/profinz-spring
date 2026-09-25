package com.coyotai.education.util;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordUtilsTest {
    private final PasswordUtils utility = new PasswordUtils();
    private final BCryptPasswordEncoder loginEncoder = new BCryptPasswordEncoder();

    @Test void verifiesHashesCreatedByApplicationLoginEncoder() {
        String storedHash = loginEncoder.encode("Example-password-123");
        assertThat(utility.matchesPassword("Example-password-123", storedHash)).isTrue();
        assertThat(utility.matchesPassword("Wrong-password", storedHash)).isFalse();
    }

    @Test void generatedHashesWorkWithApplicationLoginEncoder() {
        String hash = utility.hashPassword("Example-password-123");
        assertThat(loginEncoder.matches("Example-password-123", hash)).isTrue();
        assertThat(utility.hashPassword("Example-password-123")).isNotEqualTo(hash);
    }

    @Test void missingValuesDoNotMatch() {
        assertThat(utility.matchesPassword(null, "hash")).isFalse();
        assertThat(utility.matchesPassword("password", null)).isFalse();
        assertThat(utility.matchesPassword("password", " ")).isFalse();
    }
}