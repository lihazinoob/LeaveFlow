package com.misl.leaveflow.auth.refresh;

/**
 * The refresh token is missing, unknown, expired, revoked or belongs to a deactivated account.
 * The client must log in again. Mapped to HTTP 401 by AuthController.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
