package com.coyotai.education.fee;

public enum InstallmentStatus {
    PENDING,
    PARTIAL,
    PAID,
    OVERDUE,
    /** Written off; no longer counted as outstanding. */
    WAIVED
}
