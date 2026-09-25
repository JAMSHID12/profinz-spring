package com.coyotai.education.attendance;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Reads historical reason codes while exposing only the current informed-status choices. */
@Converter
public class AbsenceReasonConverter implements AttributeConverter<AbsenceReason, String> {
    @Override
    public String convertToDatabaseColumn(AbsenceReason value) {
        return value == null ? null : value.name();
    }

    @Override
    public AbsenceReason convertToEntityAttribute(String value) {
        if (value == null) return null;
        return switch (value) {
            // These reasons do not establish that the centre was informed.
            case "MEDICAL", "PERSONAL", "OTHER" -> AbsenceReason.NOT_INFORMED;
            default -> AbsenceReason.valueOf(value);
        };
    }
}
