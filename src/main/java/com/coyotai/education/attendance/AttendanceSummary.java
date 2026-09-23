package com.coyotai.education.attendance;

import java.util.Map;

/**
 * Attendance counters. Percentage = (present + late) / total, so an excused absence still
 * counts as a missed class - e.g. 46 present, 2 late, 3 absent, 1 excused -> 48/52 = 92.3%.
 */
public record AttendanceSummary(long totalClasses, long present, long absent, long late, long excused,
                                double attendancePercentage) {

    public static AttendanceSummary of(long present, long absent, long late, long excused) {
        long total = present + absent + late + excused;
        double percentage = total == 0 ? 0d : Math.round((present + late) * 1000d / total) / 10d;
        return new AttendanceSummary(total, present, absent, late, excused, percentage);
    }

    public static AttendanceSummary fromCounts(Map<AttendanceStatus, Long> counts) {
        return of(counts.getOrDefault(AttendanceStatus.PRESENT, 0L),
                counts.getOrDefault(AttendanceStatus.ABSENT, 0L),
                counts.getOrDefault(AttendanceStatus.LATE, 0L),
                counts.getOrDefault(AttendanceStatus.EXCUSED, 0L));
    }

    public boolean hasData() {
        return totalClasses > 0;
    }
}
