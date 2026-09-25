package com.coyotai.education.attendance;

import com.coyotai.education.notification.NotificationEvent;

public enum AttendanceStatus {
    PRESENT,
    ABSENT,
    LATE,
    /** An allowed absence (e.g. approved leave). No message to the parent. */
    EXCUSED,
    /** Not required to attend; excluded from the attendance denominator. */
    HOLIDAY;

    /** Late arrivals count as attended; absences, excused or not, do not. */
    public boolean countsAsAttended() {
        return this == PRESENT || this == LATE;
    }

    /** Absent or excused: the student was not there. */
    public boolean isAway() {
        return this == ABSENT || this == EXCUSED;
    }

    /** The parent-facing event this status raises, or null when none. */
    public NotificationEvent notificationEvent() {
        return switch (this) {
            case ABSENT -> NotificationEvent.STUDENT_ABSENT;
            case LATE -> NotificationEvent.STUDENT_LATE;
            default -> null;
        };
    }
}
