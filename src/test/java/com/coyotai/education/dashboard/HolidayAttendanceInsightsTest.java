package com.coyotai.education.dashboard;

import com.coyotai.education.attendance.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HolidayAttendanceInsightsTest {
    @Test void holidayNeverOverridesPresentOrCountsAsAbsent() {
        var repository = mock(AttendanceRepository.class);
        var day = LocalDate.of(2026, 9, 24);
        var ids = List.of(1L);
        when(repository.findDayMarks(day, ids)).thenReturn(List.of(
                new Object[]{1L, AttendanceStatus.PRESENT, null},
                new Object[]{1L, AttendanceStatus.HOLIDAY, null},
                new Object[]{2L, AttendanceStatus.HOLIDAY, null}));
        var service = new AttendanceInsightsService(repository, null, null, null, null, null);
        Object snapshot = ReflectionTestUtils.invokeMethod(service, "todaySnapshot", day, ids, 2L);
        assertThat((Long) ReflectionTestUtils.invokeMethod(snapshot, "present")).isEqualTo(1L);
        assertThat((Long) ReflectionTestUtils.invokeMethod(snapshot, "absent")).isZero();
    }
    @Test void holidayOnlyStudentHasNoAttendanceRisk() {
        var summary = AttendanceSummary.fromCounts(Map.of(AttendanceStatus.HOLIDAY, 8L));
        assertThat(AttendanceInsightsService.riskOf(summary, 0, 0).name()).isEqualTo("OK");
    }
}
