package com.coyotai.education.attendance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class AbsenceReasonConverterTest {
    private final AbsenceReasonConverter converter = new AbsenceReasonConverter();

    @ParameterizedTest
    @ValueSource(strings = {"MEDICAL", "PERSONAL", "OTHER"})
    void readsLegacyReasonsWithoutReintroducingRemovedOptions(String legacy) {
        assertThat(converter.convertToEntityAttribute(legacy)).isEqualTo(AbsenceReason.NOT_INFORMED);
    }

    @Test void roundTripsCurrentValuesAndNull() {
        for (AbsenceReason value : AbsenceReason.values()) {
            assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(value))).isEqualTo(value);
        }
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(AbsenceReason.values()).containsExactly(AbsenceReason.INFORMED, AbsenceReason.NOT_INFORMED);
    }

    @Test void doesNotHideUnrecognizedDatabaseCorruption() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
