package com.coyotai.education.platform;

import lombok.Getter;
import lombok.Setter;

/** Look and feel of the client ({@code project.branding.*}). Read by the frontend only via the API. */
@Getter
@Setter
public class BrandingProperties {

    /** Path of the logo, served by the frontend (for example /assets/branding/logo.svg). */
    private String logo;

    /** Main brand colour as a hex value; the UI derives its palette from it. */
    private String primaryColor = "#1d4f91";

    /** Optional login screen message. */
    private String loginMessage;
}
