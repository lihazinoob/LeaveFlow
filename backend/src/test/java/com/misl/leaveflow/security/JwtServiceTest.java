package com.misl.leaveflow.security;

import com.misl.leaveflow.employee.Role;
import com.misl.leaveflow.support.TestEmployees;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "XkTeVzddfqNkK+/oQvr7EGhYRcRo4IVLNSQmxkeG/uI=";
    private static final String OTHER_SECRET = "c2VjcmV0LWtleS10aGF0LWlzLWRpZmZlcmVudC0zMmI=";

    private final EmployeeUserDetails user = EmployeeUserDetails.fromEmployee(
            TestEmployees.employee(7L, "rakib@leaveflow.com", Role.MANAGER));

    private static JwtService serviceWith(String secret, Duration accessTokenLifetime) {
        return new JwtService(new JwtProperties(secret, accessTokenLifetime, Duration.ofDays(7), false));
    }

    @Test
    void accessToken_roundTrip_rebuildsUserFromClaims() {
        JwtService jwtService = serviceWith(SECRET, Duration.ofMinutes(15));

        JwtService.AccessToken issued = jwtService.generateAccessToken(user);
        EmployeeUserDetails parsed = jwtService.parseAccessToken(issued.token());

        assertThat(parsed.getId()).isEqualTo(7L);
        assertThat(parsed.getEmail()).isEqualTo("rakib@leaveflow.com");
        assertThat(parsed.getFullName()).isEqualTo(user.getFullName());
        assertThat(parsed.getRole()).isEqualTo(Role.MANAGER);
        assertThat(parsed.getAuthorities()).extracting("authority").containsExactly("ROLE_MANAGER");
        assertThat(parsed.getPassword()).isNull();
        assertThat(parsed.isEnabled()).isTrue();
    }

    @Test
    void accessToken_expiresAfterConfiguredLifetime() {
        JwtService jwtService = serviceWith(SECRET, Duration.ofMinutes(15));

        Instant before = Instant.now();
        JwtService.AccessToken issued = jwtService.generateAccessToken(user);

        assertThat(issued.expiresAt()).isBetween(before.plus(Duration.ofMinutes(14)), before.plus(Duration.ofMinutes(16)));
    }

    @Test
    void expiredToken_throwsExpiredJwtException() {
        JwtService jwtService = serviceWith(SECRET, Duration.ofSeconds(-5));

        String token = jwtService.generateAccessToken(user).token();

        assertThatThrownBy(() -> jwtService.parseAccessToken(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithAnotherSecret_isRejected() {
        String token = serviceWith(OTHER_SECRET, Duration.ofMinutes(15)).generateAccessToken(user).token();
        JwtService verifier = serviceWith(SECRET, Duration.ofMinutes(15));

        assertThatThrownBy(() -> verifier.parseAccessToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedToken_isRejected() {
        JwtService jwtService = serviceWith(SECRET, Duration.ofMinutes(15));
        String token = jwtService.generateAccessToken(user).token();

        // Change one character of the payload section; the signature no longer matches.
        String[] parts = token.split("\\.");
        char flipped = parts[1].charAt(5) == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1].substring(0, 5) + flipped + parts[1].substring(6) + "." + parts[2];

        assertThatThrownBy(() -> jwtService.parseAccessToken(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void garbageOrEmptyToken_isRejected() {
        JwtService jwtService = serviceWith(SECRET, Duration.ofMinutes(15));

        assertThatThrownBy(() -> jwtService.parseAccessToken("not-a-jwt")).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwtService.parseAccessToken("")).isInstanceOf(JwtException.class);
    }
}
