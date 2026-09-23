package com.coyotai.education.platform;

/**
 * Business areas that can be switched on or off per client with
 * {@code project.module.<name>.enabled}. A disabled module is rejected by the API.
 */
public enum ModuleCode {

    ACADEMICS("Academics"),
    ADMINISTRATION("Administration"),
    FEES("Fees"),
    NOTIFICATIONS("Notifications"),
    REPORTS("Reports"),
    STUDENT_PORTAL("Student portal"),
    SALES("Sales"),
    ACCOUNTS("Accounts"),
    DIRECTORS("Directors");

    private final String displayName;

    ModuleCode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
