package com.misl.leaveflow.auth.refresh;

/**
 * Why a refresh token stopped being valid.
 * Only {@link #ROTATED} matters for security: a rotated token should never be seen again,
 * so if it comes back it was probably copied by someone else (reuse detection).
 */
public enum RevokedReason {
    /** Replaced by a new token during a normal refresh. */
    ROTATED,
    /** The user logged out of this session. */
    LOGOUT,
    /** The user changed their password, which signs out all other sessions. */
    PASSWORD_CHANGED,
    /** An admin deactivated the account. */
    ACCOUNT_DEACTIVATED,
    /** A rotated token was replayed, so every session of the user was shut down. */
    REUSE_DETECTED
}
