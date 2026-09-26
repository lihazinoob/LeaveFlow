package com.misl.leaveflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and verifies JSON Web Tokens.
 * <p>
 * A token is signed with HMAC-SHA256 using a server-side secret, so the server can later
 * verify it was not tampered with, without storing any session.
 * The subject is the user's email; role and name are extra claims for the frontend.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
    }

    public IssuedToken generateToken(EmployeeUserDetails user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.expiration());

        String token = Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .claim("name", user.getFullName())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();

        return new IssuedToken(token, expiresAt);
    }

    /**
     * Returns the email stored in a token, or empty if the token is malformed,
     * has a bad signature, or has expired.
     */
    public Optional<String> extractValidSubject(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.ofNullable(claims.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
