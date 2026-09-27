package com.misl.leaveflow.security;

import com.misl.leaveflow.employee.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and verifies access tokens (JSON Web Tokens).
 * <p>
 * An access token is signed with HMAC-SHA256 using a server-side secret. It carries everything
 * needed to authorise a request (user id, email, name, role), so the server can trust it
 * without a database lookup. Because it cannot be revoked, it is short-lived (15 minutes by default);
 * the client obtains a new one with its refresh token.
 */
@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
    }

    public AccessToken generateAccessToken(EmployeeUserDetails user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.accessTokenExpiration());

        String token = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_NAME, user.getFullName())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();

        return new AccessToken(token, expiresAt);
    }

    /**
     * Verifies the signature and expiry, then rebuilds the user from the claims.
     *
     * @throws io.jsonwebtoken.ExpiredJwtException if the token is well-formed but expired
     * @throws JwtException                        if the token is malformed, tampered with or signed by another key
     */
    public EmployeeUserDetails parseAccessToken(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (IllegalArgumentException ex) {
            // jjwt throws IllegalArgumentException for null/blank input; report it like any other bad token.
            throw new JwtException("Access token is empty", ex);
        }

        try {
            return EmployeeUserDetails.fromTokenClaims(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    claims.get(CLAIM_NAME, String.class),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class)));
        } catch (RuntimeException ex) {
            // Correctly signed but missing/invalid claims: never happens for our own tokens.
            throw new JwtException("Access token has invalid claims", ex);
        }
    }

    public record AccessToken(String token, Instant expiresAt) {
    }
}
