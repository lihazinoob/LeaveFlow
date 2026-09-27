package com.misl.leaveflow.auth.refresh;

import com.misl.leaveflow.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds the Set-Cookie header that carries the refresh token.
 * <ul>
 *   <li>HttpOnly: JavaScript cannot read it, so an XSS bug cannot steal the long-lived token.</li>
 *   <li>SameSite=Strict: the browser never sends it on requests started by another site (blocks CSRF).</li>
 *   <li>Path=/api/auth: it is only sent to the auth endpoints, not to every API call.</li>
 *   <li>Secure: HTTPS-only in production (configurable so plain-http localhost still works).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieFactory {

    public static final String COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/auth";

    private final JwtProperties jwtProperties;

    public ResponseCookie create(String rawToken) {
        return base(rawToken).maxAge(jwtProperties.refreshTokenExpiration()).build();
    }

    /** An empty cookie with Max-Age=0 tells the browser to delete it. */
    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(jwtProperties.cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH);
    }
}
