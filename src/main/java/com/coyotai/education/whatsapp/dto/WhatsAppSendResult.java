package com.coyotai.education.whatsapp.dto;

/** Outcome of one send attempt. Failures are reported, never thrown, to the queue. */
public record WhatsAppSendResult(boolean success, String providerMessageId, String errorMessage) {

    public static WhatsAppSendResult success(String providerMessageId) {
        return new WhatsAppSendResult(true, providerMessageId, null);
    }

    public static WhatsAppSendResult failure(String errorMessage) {
        return new WhatsAppSendResult(false, null, errorMessage);
    }
}
