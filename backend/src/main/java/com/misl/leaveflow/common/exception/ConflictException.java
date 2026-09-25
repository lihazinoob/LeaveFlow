package com.misl.leaveflow.common.exception;

/** Thrown when a write clashes with existing data (e.g. duplicate email). Mapped to HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
