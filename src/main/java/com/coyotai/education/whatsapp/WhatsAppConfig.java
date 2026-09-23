package com.coyotai.education.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Chooses the WhatsApp implementation at startup. WHATSAPP_ENABLED=false
 * keeps the application fully usable without WABI credentials.
 */
@Configuration
public class WhatsAppConfig {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppConfig.class);

    @Bean
    public WhatsAppService whatsAppService(RestTemplate whatsAppRestTemplate,
                                           WhatsAppProperties properties) {
        if (!properties.enabled()) {
            log.info("WhatsApp integration: mock mode. No external messages are sent.");
            return new MockWhatsAppService();
        }
        if (!properties.isLive()) throw new IllegalStateException("WhatsApp is enabled but provider credentials are missing. Configure WABI_API_KEY for WABI, or explicitly disable WhatsApp.");
        log.info("WhatsApp integration: WABI at {} for {}", properties.wabi().baseUrl(), properties.wabi().event());
        return new WabiWhatsAppService(whatsAppRestTemplate, properties);
    }
}
