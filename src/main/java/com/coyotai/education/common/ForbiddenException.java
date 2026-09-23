package com.coyotai.education.common;

/**
 * The caller is signed in and holds the permission, but the record lies outside their data
 * scope (another mentor's batch, another student's results, ...).
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }

    public static ForbiddenException outOfScope(String what) {
        return new ForbiddenException("You do not have access to this " + what);
    }
}
