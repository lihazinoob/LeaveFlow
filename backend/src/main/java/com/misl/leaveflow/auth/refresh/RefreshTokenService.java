package com.misl.leaveflow.auth.refresh;

import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issues, rotates and revokes refresh tokens.
 * <p>
 * A refresh token is 256 bits from a cryptographically secure random generator, sent to the
 * browser in an httpOnly cookie. Only its SHA-256 hash is stored. The token is already
 * unguessable, so a fast hash is enough (no salt or BCrypt needed).
 * <p>
 * <b>Rotation:</b> each successful refresh revokes the presented token (reason ROTATED) and issues a new one.
 * <b>Reuse detection:</b> a rotated token should never come back. If it does, it was probably
 * copied by someone else, so every active token of that employee is revoked and they must log in again.
 * Tokens revoked for other reasons (logout, password change, ...) are simply rejected.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    /** Creates a new refresh token for the employee and returns the raw value (to be put in the cookie). */
    @Transactional
    public String issue(Employee employee) {
        String rawToken = generateRawToken();
        Instant expiresAt = Instant.now().plus(jwtProperties.refreshTokenExpiration());
        refreshTokenRepository.save(new RefreshToken(employee, hash(rawToken), expiresAt));
        return rawToken;
    }

    /**
     * Validates the presented token, revokes it, and issues a replacement.
     * {@code noRollbackFor}: when reuse is detected we revoke all tokens and then throw;
     * that revocation must still be committed.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken) {
        RefreshToken current = findByRawToken(rawToken);
        Instant now = Instant.now();
        Employee employee = current.getEmployee();

        if (current.isRevoked()) {
            if (current.getRevokedReason() == RevokedReason.ROTATED) {
                log.warn("Rotated refresh token reused for employee {}; revoking all sessions", employee.getId());
                refreshTokenRepository.revokeAllActiveByEmployeeId(employee.getId(), now, RevokedReason.REUSE_DETECTED);
                throw new InvalidRefreshTokenException("Refresh token has already been used. Please log in again.");
            }
            throw new InvalidRefreshTokenException("Session has ended. Please log in again.");
        }
        if (current.isExpired(now)) {
            throw new InvalidRefreshTokenException("Session has expired. Please log in again.");
        }
        if (!employee.isActive()) {
            current.revoke(now, RevokedReason.ACCOUNT_DEACTIVATED);
            throw new InvalidRefreshTokenException(
                    "Your account has been deactivated. Please contact the administrator.");
        }

        current.revoke(now, RevokedReason.ROTATED);
        return new Rotation(employee, issue(employee));
    }

    /** Logout: revokes the given token if it exists. Unknown or already-revoked tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> token.revoke(Instant.now(), RevokedReason.LOGOUT));
    }

    /** Logs an employee out everywhere, e.g. after a password change or deactivation. */
    @Transactional
    public void revokeAllForEmployee(Long employeeId, RevokedReason reason) {
        refreshTokenRepository.revokeAllActiveByEmployeeId(employeeId, Instant.now(), reason);
    }

    private RefreshToken findByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token is missing. Please log in.");
        }
        return refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token. Please log in again."));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    /** Result of a successful rotation: who the token belonged to and their new raw refresh token. */
    public record Rotation(Employee employee, String newRefreshToken) {
    }
}
