package com.coyotai.education.util;

/** Phone-number helpers shared by validation and the WhatsApp integration. */
public final class PhoneNumbers {

    /** Digits, optionally prefixed with '+', 7-15 long (ITU E.164 maximum). */
    public static final String PATTERN = "^\\+?[0-9]{7,15}$";

    private PhoneNumbers() {
    }

    public static boolean isValid(String value) {
        return value != null && value.trim().matches(PATTERN);
    }

    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Normalises a number for the WhatsApp Cloud API, which expects digits only
     * (country code included, no '+', spaces or dashes).
     */
    public static String toWhatsAppFormat(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }
}
