package com.misl.leaveflow.auth.dto;

import java.time.Instant;

/**
 * Returned by login and refresh. The frontend keeps the access token in memory and sends it as
 * "Authorization: Bearer &lt;accessToken&gt;". The refresh token is not in this body: it travels
 * separately in an httpOnly cookie that JavaScript cannot read.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        CurrentUserResponse user
) {
}
