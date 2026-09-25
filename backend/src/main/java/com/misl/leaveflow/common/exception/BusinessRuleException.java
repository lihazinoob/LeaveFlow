package com.misl.leaveflow.common.exception;

/**
 * Thrown when a request is well-formed but breaks a business rule
 * (e.g. overlapping leave, insufficient balance). Mapped to HTTP 400.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
