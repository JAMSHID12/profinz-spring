package com.coyotai.education.dashboard;

import com.coyotai.education.attendance.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AttendanceInsightsCompatibilityTest {
    @Test void overviewCountsExplicitNotInformedAndHistoricalMissingReasons() {
        var repository = mock(AttendanceRepository.class);
        var day = LocalDate.of(2026, 9, 24);
        var ids = List.of(1L);
        var legacy = new AbsenceReasonConverter().convertToEntityAttribute("PERSONAL");
        when(repository.findDayMarks(day, ids)).thenReturn(List.of(
                new Object[]{1L, AttendanceStatus.ABSENT, AbsenceReason.INFORMED},
                new Object[]{2L, AttendanceStatus.ABSENT, AbsenceReason.NOT_INFORMED},
                new Object[]{3L, AttendanceStatus.ABSENT, null},
                new Object[]{4L, AttendanceStatus.ABSENT, legacy},
                new Object[]{5L, AttendanceStatus.PRESENT, null}));
        var service = new AttendanceInsightsService(repository, null, null, null, null, null);
        Object snapshot = ReflectionTestUtils.invokeMethod(service, "todaySnapshot", day, ids, 6L);
        assertThat((Long) ReflectionTestUtils.invokeMethod(snapshot, "absentWithoutReason")).isEqualTo(3L);
    }
}
