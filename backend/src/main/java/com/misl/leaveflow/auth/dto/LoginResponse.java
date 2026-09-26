package com.misl.leaveflow.auth.dto;

import java.time.Instant;

/** Returned after a successful login. The frontend stores the token and sends it as "Bearer &lt;token&gt;". */
public record LoginResponse(
        String token,
        String tokenType,
        Instant expiresAt,
        CurrentUserResponse user
) {
}
