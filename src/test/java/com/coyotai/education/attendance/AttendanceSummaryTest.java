package com.coyotai.education.attendance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceSummaryTest {

    @Test
    @DisplayName("The specification example: 46 present, 3 absent, 2 late, 1 excused is 92%")
    void specificationExample() {
        AttendanceSummary summary = AttendanceSummary.of(46, 3, 2, 1);
        assertThat(summary.totalClasses()).isEqualTo(52);
        assertThat(summary.attendancePercentage()).isEqualTo(92.3);
    }

    @Test
    @DisplayName("No classes means no percentage rather than a misleading 0%")
    void noData() {
        assertThat(AttendanceSummary.of(0, 0, 0, 0).hasData()).isFalse();
    }

    @Test
    @DisplayName("Only absences and late arrivals notify parents")
    void notifyingStatuses() {
        assertThat(AttendanceStatus.ABSENT.notificationEvent()).isNotNull();
        assertThat(AttendanceStatus.LATE.notificationEvent()).isNotNull();
        assertThat(AttendanceStatus.PRESENT.notificationEvent()).isNull();
        assertThat(AttendanceStatus.EXCUSED.notificationEvent()).isNull();
    }
}
