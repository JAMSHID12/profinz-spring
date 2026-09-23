package com.coyotai.education.common;

/** Lifecycle of simple master data. Records are deactivated, never deleted, to keep history. */
public enum RecordStatus {
    ACTIVE,
    INACTIVE
}
