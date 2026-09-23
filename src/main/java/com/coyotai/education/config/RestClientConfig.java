package com.coyotai.education.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    /**
     * Dedicated client for outbound WABI calls. Timeouts are short so a
     * slow WABI endpoint can never stall the notification scheduler for long.
     */
    @Bean
    public RestTemplate whatsAppRestTemplate(RestTemplateBuilder builder,
                                             com.coyotai.education.whatsapp.WhatsAppProperties properties) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds()))
                .build();
    }
}
