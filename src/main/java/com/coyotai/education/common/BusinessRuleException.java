package com.coyotai.education.common;

/** Thrown when a request is well-formed but violates a domain rule. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
