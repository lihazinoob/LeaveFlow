package com.misl.leaveflow.common.exception;

/** Thrown when a requested record does not exist. Mapped to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, Object id) {
        super(resourceName + " not found with id " + id);
    }
}
