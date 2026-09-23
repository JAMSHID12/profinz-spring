package com.coyotai.education.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Technical settings that are the same kind for every client ({@code app.*}). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors) {

    public record Jwt(String secret, long accessTokenMinutes, long refreshTokenDays) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
