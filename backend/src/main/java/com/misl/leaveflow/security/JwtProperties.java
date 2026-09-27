package com.misl.leaveflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Token settings bound from {@code app.jwt.*} in application.yml.
 *
 * @param secret                 Base64-encoded HMAC key used to sign access tokens (at least 256 bits for HS256)
 * @param accessTokenExpiration  lifetime of an access token, e.g. "15m"; kept short because it cannot be revoked
 * @param refreshTokenExpiration lifetime of a refresh token, e.g. "7d"; how long a user stays logged in when idle
 * @param cookieSecure           whether the refresh-token cookie is HTTPS-only; true in production,
 *                               false for local http://localhost development
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        Duration accessTokenExpiration,
        Duration refreshTokenExpiration,
        boolean cookieSecure
) {
}
