package com.coyotai.education.whatsapp;

import com.coyotai.education.util.PhoneNumbers;
import com.coyotai.education.whatsapp.dto.NotificationPayload;
import com.coyotai.education.whatsapp.dto.WhatsAppSendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Development sender. Logs the message instead of calling WABI, so the whole application
 * can be run and tested with no WhatsApp credentials.
 */
public class MockWhatsAppService implements WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(MockWhatsAppService.class);

    @Override
    public WhatsAppSendResult send(String toPhoneNumber, NotificationPayload payload) {
        String number = PhoneNumbers.toWhatsAppFormat(toPhoneNumber);
        if (number == null) {
            return WhatsAppSendResult.failure("Missing or invalid phone number");
        }
        log.info("[MOCK WHATSAPP]\nTo: {}\nTemplate: {}\nParameters: {}\nMessage: {}",
                number, payload.templateName(), payload.parameters(), payload.preview());
        return WhatsAppSendResult.success("mock-" + UUID.randomUUID());
    }

    @Override
    public String mode() {
        return "MOCK";
    }
}
