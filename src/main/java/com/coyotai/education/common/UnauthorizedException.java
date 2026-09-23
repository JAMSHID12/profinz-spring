package com.coyotai.education.common;

/** Thrown when a credential or token is rejected and the client should sign in again. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
