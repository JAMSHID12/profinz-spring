package com.coyotai.education.platform;

import lombok.Getter;
import lombok.Setter;

/** Identity of the client this deployment serves ({@code project.client.*}). */
@Getter
@Setter
public class ClientProperties {

    /** Stable identifier, also the tenant key in the database. */
    private String code;

    /** Display name shown in the UI and in messages. */
    private String name;

    private String tagline;

    /** Wall-clock timezone used for "today" (attendance, due dates, schedules). */
    private String timezone = "UTC";

    private String currency = "INR";

    private String locale = "en-IN";
}
