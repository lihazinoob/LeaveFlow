package com.misl.leaveflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings bound from {@code app.jwt.*} in application.yml.
 *
 * @param secret     Base64-encoded HMAC key (at least 256 bits for HS256)
 * @param expiration how long an issued token stays valid, e.g. "8h"
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration expiration) {
}
