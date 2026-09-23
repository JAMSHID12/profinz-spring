package com.coyotai.education.security;

import com.coyotai.education.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(new AppProperties(
            new AppProperties.Jwt("unit-test-secret-that-is-comfortably-long-enough", 60, 7),
            new AppProperties.Cors(List.of())));

    @Test
    @DisplayName("An access token round-trips for its own client")
    void roundTrip() {
        String token = jwtService.generateAccessToken("admin", "PROFINZ");
        assertThat(jwtService.extractUsername(token, false, "PROFINZ")).isEqualTo("admin");
    }

    @Test
    @DisplayName("A token issued for one client is rejected by another client's deployment")
    void otherClientRejected() {
        String token = jwtService.generateAccessToken("admin", "PROFINZ");
        assertThat(jwtService.extractUsername(token, false, "ACME")).isNull();
    }

    @Test
    @DisplayName("Access and refresh tokens cannot be swapped")
    void typesAreSeparate() {
        String access = jwtService.generateAccessToken("admin", "PROFINZ");
        String refresh = jwtService.generateRefreshToken("admin", "PROFINZ");
        assertThat(jwtService.extractUsername(access, true, "PROFINZ")).isNull();
        assertThat(jwtService.extractUsername(refresh, false, "PROFINZ")).isNull();
        assertThat(jwtService.extractUsername(refresh, true, "PROFINZ")).isEqualTo("admin");
    }

    @Test
    @DisplayName("Garbage is rejected without throwing")
    void garbage() {
        assertThat(jwtService.extractUsername("not-a-token", false, "PROFINZ")).isNull();
    }

    @Test
    @DisplayName("A short signing secret fails fast")
    void shortSecret() {
        assertThatThrownBy(() -> new JwtService(new AppProperties(new AppProperties.Jwt("short", 60, 7),
                new AppProperties.Cors(List.of())))).isInstanceOf(IllegalStateException.class);
    }
}
