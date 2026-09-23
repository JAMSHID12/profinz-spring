package com.coyotai.education.common;

/** Thrown when an outbound integration (WhatsApp Cloud API) fails. */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
