package com.misl.leaveflow.security;

import com.misl.leaveflow.employee.Role;
import com.misl.leaveflow.support.TestEmployees;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "XkTeVzddfqNkK+/oQvr7EGhYRcRo4IVLNSQmxkeG/uI=";
    private static final String OTHER_SECRET = "c2VjcmV0LWtleS10aGF0LWlzLWRpZmZlcmVudC0zMmI=";

    private final EmployeeUserDetails user =
            new EmployeeUserDetails(TestEmployees.employee(7L, "rakib@leaveflow.com", Role.EMPLOYEE));

    @Test
    void generatedToken_isValidAndCarriesEmailAsSubject() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, Duration.ofHours(8)));

        JwtService.IssuedToken issued = jwtService.generateToken(user);

        assertThat(jwtService.extractValidSubject(issued.token())).contains("rakib@leaveflow.com");
        assertThat(issued.expiresAt()).isAfter(Instant.now().plus(Duration.ofHours(7)));
    }

    @Test
    void expiredToken_isRejected() {
        JwtService expiringService = new JwtService(new JwtProperties(SECRET, Duration.ofSeconds(-5)));

        String token = expiringService.generateToken(user).token();

        assertThat(expiringService.extractValidSubject(token)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecret_isRejected() {
        JwtService issuer = new JwtService(new JwtProperties(OTHER_SECRET, Duration.ofHours(1)));
        JwtService verifier = new JwtService(new JwtProperties(SECRET, Duration.ofHours(1)));

        String token = issuer.generateToken(user).token();

        assertThat(verifier.extractValidSubject(token)).isEmpty();
    }

    @Test
    void tamperedToken_isRejected() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, Duration.ofHours(1)));
        String token = jwtService.generateToken(user).token();

        // Change one character of the payload section; the signature no longer matches.
        String[] parts = token.split("\\.");
        char flipped = parts[1].charAt(5) == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1].substring(0, 5) + flipped + parts[1].substring(6) + "." + parts[2];

        assertThat(jwtService.extractValidSubject(tampered)).isEmpty();
    }

    @Test
    void garbageToken_isRejected() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, Duration.ofHours(1)));

        assertThat(jwtService.extractValidSubject("not-a-jwt")).isEmpty();
        assertThat(jwtService.extractValidSubject("")).isEmpty();
    }
}
