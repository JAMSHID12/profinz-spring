package com.coyotai.education.notification;

/**
 * Domain events that can produce a message. Academic and fee code only raise events;
 * which channels carry them is configuration ({@code project.notifications.events}).
 */
public enum NotificationEvent {
    STUDENT_ABSENT,
    STUDENT_LATE,
    FEE_DUE,
    PAYMENT_RECEIVED,
    EXAM_RESULT_PUBLISHED,
    PROGRESS_CARD_PUBLISHED,
    FINE_CREATED,
    CLASS_SCHEDULE_CHANGED,
    GENERAL
}
