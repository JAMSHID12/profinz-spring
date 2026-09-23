package com.coyotai.education.whatsapp.dto;

import java.util.List;

/**
 * Serialised into notifications.message_payload. Holds everything needed to send the
 * message later on any channel, plus the human-readable text shown in logs and in-app.
 */
public record NotificationPayload(
        String templateName,
        String languageCode,
        List<String> parameters,
        String preview
) {
}
