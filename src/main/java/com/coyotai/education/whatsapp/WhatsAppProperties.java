package com.coyotai.education.whatsapp;

import com.coyotai.education.notification.NotificationEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/** All WhatsApp settings come from environment variables. Nothing is hard-coded. */
@ConfigurationProperties(prefix = "whatsapp")
public record WhatsAppProperties(
        boolean enabled,
        Provider provider,
        Wabi wabi,
        String defaultLanguage,
        long connectTimeoutSeconds,
        long readTimeoutSeconds,
        Map<NotificationEvent, String> templates,
        Queue queue
) {

    public enum Provider { WABI }

    public record Wabi(String baseUrl, String apiKey, NotificationEvent event, String optInSource,
                       Map<NotificationEvent, String> eventApiKeys) {
        @org.springframework.boot.context.properties.bind.ConstructorBinding
        public Wabi { }

        public Wabi(String baseUrl, String apiKey, NotificationEvent event, String optInSource) {
            this(baseUrl, apiKey, event, optInSource, Map.of());
        }

        public String keyFor(NotificationEvent requested) {
            String specific = eventApiKeys == null ? null : eventApiKeys.get(requested);
            if (specific != null && !specific.isBlank()) return specific;
            return requested == event ? apiKey : null;
        }

        public boolean hasKey() {
            return (apiKey != null && !apiKey.isBlank()) || (eventApiKeys != null
                    && eventApiKeys.values().stream().anyMatch(key -> key != null && !key.isBlank()));
        }

        @Override public String toString() { return "Wabi[baseUrl=" + baseUrl + ", apiKey=[redacted], event=" + event + "]"; }
    }

    public boolean usesWabi() { return provider == Provider.WABI; }

    /** A WABI integration key is bound to one template; never use a late key for another event. */
    public boolean supportsEvent(NotificationEvent event) {
        return !enabled || !usesWabi() || (wabi != null && wabi.keyFor(event) != null && !wabi.keyFor(event).isBlank());
    }

    public record Queue(
            boolean enabled,
            int fixedDelaySeconds,
            int batchSize,
            int maxRetries,
            int retryBackoffMinutes
    ) {
    }

    /** True when real credentials are present and the integration is switched on. */
    public boolean isLive() {
        return usesWabi() && enabled && wabi != null && wabi.hasKey()
                && wabi.baseUrl() != null && !wabi.baseUrl().isBlank() && wabi.event() != null;
    }

    /** The approved provider template name configured for an event. */
    public String templateName(NotificationEvent event) {
        String name = templates == null ? null : templates.get(event);
        return name == null ? event.name().toLowerCase() : name;
    }
}
