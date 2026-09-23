package com.coyotai.education.whatsapp;

import com.coyotai.education.whatsapp.dto.NotificationPayload;
import com.coyotai.education.whatsapp.dto.WhatsAppSendResult;

/**
 * The single seam between the application and WhatsApp. Swapping the mock for the real
 * WABI client is a configuration change, not a code change.
 */
public interface WhatsAppService {

    WhatsAppSendResult send(String toPhoneNumber, NotificationPayload payload);

    default WhatsAppSendResult send(String toPhoneNumber, NotificationPayload payload,
            com.coyotai.education.notification.NotificationEvent event, String externalId) {
        return send(toPhoneNumber, payload);
    }

    /** Human-readable mode shown on the settings screen: MOCK or WABI. */
    String mode();
}
