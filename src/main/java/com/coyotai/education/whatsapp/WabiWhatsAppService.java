package com.coyotai.education.whatsapp;

import com.coyotai.education.notification.NotificationEvent;
import com.coyotai.education.util.PhoneNumbers;
import com.coyotai.education.whatsapp.dto.NotificationPayload;
import com.coyotai.education.whatsapp.dto.WhatsAppSendResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpStatusCodeException;
import java.net.URI;
import java.util.*;

/** WABI integration API: the API key selects the approved template; external_id deduplicates retries. */
public class WabiWhatsAppService implements WhatsAppService {
    private final RestTemplate client;
    private final WhatsAppProperties properties;
    private final String endpoint;

    public WabiWhatsAppService(RestTemplate client, WhatsAppProperties properties) {
        this.client = client;
        this.properties = properties;
        URI base = URI.create(properties.wabi().baseUrl());
        if (!"https".equalsIgnoreCase(base.getScheme()) || base.getHost() == null || base.getUserInfo() != null
                || base.getQuery() != null || base.getFragment() != null)
            throw new IllegalStateException("WABI_BASE_URL must be an HTTPS URL without credentials, query or fragment");
        endpoint = properties.wabi().baseUrl().replaceAll("/+$", "") + "/api/v1/integrations/messages";
        if (properties.wabi().optInSource() == null || properties.wabi().optInSource().isBlank())
            throw new IllegalStateException("WABI_OPT_IN_SOURCE is required");
    }

    @Override public String mode() { return "WABI"; }

    @Override public WhatsAppSendResult send(String to, NotificationPayload payload) {
        return send(to, payload, properties.wabi().event(), "education-test-" + UUID.randomUUID());
    }

    @Override public WhatsAppSendResult send(String to, NotificationPayload payload, NotificationEvent event, String externalId) {
        if (event != properties.wabi().event()) return WhatsAppSendResult.failure("WABI key is configured only for " + properties.wabi().event());
        if (!Objects.equals(payload.templateName(), properties.templateName(event)))
            return WhatsAppSendResult.failure("Queued template does not match the configured WABI template. Create a notification with the current configuration.");
        String number = PhoneNumbers.toWhatsAppFormat(to);
        if (!PhoneNumbers.isValid(to) || number == null || !number.matches("[1-9][0-9]{6,14}")) return WhatsAppSendResult.failure("Missing or invalid phone number");
        if (externalId == null || externalId.isBlank() || externalId.length() > 128)
            return WhatsAppSendResult.failure("Missing or invalid notification external ID");
        if (payload.parameters() == null || payload.parameters().size() > 20)
            return WhatsAppSendResult.failure("Invalid WABI template parameters");
        if (event == NotificationEvent.STUDENT_LATE && (payload.parameters().size() != 5
                || payload.parameters().stream().anyMatch(v -> v == null || v.isBlank())))
            return WhatsAppSendResult.failure("Late template requires five non-empty parameters: parent, student, class, date, centre");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.wabi().apiKey());
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("external_id", externalId);
        body.put("to", "+" + number);
        body.put("parameters", payload.parameters());
        body.put("opt_in_source", properties.wabi().optInSource());
        if (!payload.parameters().isEmpty()) body.put("contact_name", payload.parameters().get(0));
        try {
            ResponseEntity<JsonNode> response = client.exchange(endpoint, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
            JsonNode result = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || result == null
                    || !externalId.equals(result.path("external_id").asText()))
                return WhatsAppSendResult.failure("WABI returned an invalid acknowledgement");
            String status = result.path("status").asText("").toLowerCase(Locale.ROOT);
            if (result.hasNonNull("error") && !result.path("error").asText().isBlank())
                return WhatsAppSendResult.failure("WABI: " + safe(result.path("error").asText()));
            if (!Set.of("sent", "delivered", "read", "queued", "pending", "sending", "accepted").contains(status))
                return WhatsAppSendResult.failure("WABI did not accept the message (status: " + safe(status) + ")");
            String id = result.path("wamid").asText(null);
            if (id == null) id = result.path("message_id").asText(externalId);
            return WhatsAppSendResult.success(id);
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 409)
                return WhatsAppSendResult.failure("WABI HTTP 409: this external_id already belongs to different content. Retry only unchanged content; create a new notification for new content.");
            // Never include the request headers, API key or raw response in logs/errors.
            return WhatsAppSendResult.failure("WABI HTTP " + ex.getStatusCode().value() + ": check key, template approval, opt-in and parameter order");
        } catch (RestClientException ex) {
            return WhatsAppSendResult.failure("WABI unavailable; the notification can be retried with the same external ID");
        }
    }

    private String safe(String error) {
        String clean = error.replace(properties.wabi().apiKey(), "[redacted]");
        return clean.substring(0, Math.min(300, clean.length()));
    }
}
