package com.misl.leaveflow.auth.refresh;

import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.Role;
import com.misl.leaveflow.security.JwtProperties;
import com.misl.leaveflow.support.TestEmployees;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;
    private final Employee rakib = TestEmployees.employee(7L, "rakib@leaveflow.com", Role.EMPLOYEE);

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties("unused", Duration.ofMinutes(15), Duration.ofDays(7), false);
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, properties);
    }

    @Test
    void issue_storesOnlyTheHashWithSevenDayExpiry() {
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);

        String raw = refreshTokenService.issue(rakib);

        verify(refreshTokenRepository).save(saved.capture());
        RefreshToken stored = saved.getValue();
        assertThat(raw).hasSize(43);                                // 32 random bytes, Base64url without padding
        assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(raw);
        assertThat(stored.getTokenHash()).isEqualTo(RefreshTokenService.hash(raw));
        assertThat(stored.getEmployee()).isSameAs(rakib);
        assertThat(stored.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(6)));
        assertThat(stored.isRevoked()).isFalse();
    }

    @Test
    void issue_generatesDifferentTokensEachTime() {
        assertThat(refreshTokenService.issue(rakib)).isNotEqualTo(refreshTokenService.issue(rakib));
    }

    @Test
    void rotate_revokesPresentedTokenAndIssuesANewOne() {
        RefreshToken current = storedToken("raw-old", Instant.now().plus(Duration.ofDays(1)));

        RefreshTokenService.Rotation rotation = refreshTokenService.rotate("raw-old");

        assertThat(current.isRevoked()).isTrue();
        assertThat(current.getRevokedReason()).isEqualTo(RevokedReason.ROTATED);
        assertThat(rotation.employee()).isSameAs(rakib);
        assertThat(rotation.newRefreshToken()).isNotBlank().isNotEqualTo("raw-old");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void rotate_withMissingToken_isRejected() {
        assertThatThrownBy(() -> refreshTokenService.rotate(null))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("missing");
        assertThatThrownBy(() -> refreshTokenService.rotate("  "))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotate_withUnknownToken_isRejected() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate("made-up"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Invalid refresh token. Please log in again.");
    }

    @Test
    void rotate_withAlreadyRotatedToken_isTreatedAsTheft_andRevokesEverySession() {
        RefreshToken reused = storedToken("raw-reused", Instant.now().plus(Duration.ofDays(1)));
        reused.revoke(Instant.now(), RevokedReason.ROTATED);

        assertThatThrownBy(() -> refreshTokenService.rotate("raw-reused"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Refresh token has already been used. Please log in again.");
        verify(refreshTokenRepository)
                .revokeAllActiveByEmployeeId(eq(7L), any(Instant.class), eq(RevokedReason.REUSE_DETECTED));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotate_withTokenRevokedByLogout_isRejectedWithoutRevokingOtherSessions() {
        RefreshToken loggedOut = storedToken("raw-logged-out", Instant.now().plus(Duration.ofDays(1)));
        loggedOut.revoke(Instant.now(), RevokedReason.LOGOUT);

        assertThatThrownBy(() -> refreshTokenService.rotate("raw-logged-out"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Session has ended. Please log in again.");
        verify(refreshTokenRepository, never()).revokeAllActiveByEmployeeId(any(), any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotate_withExpiredToken_isRejected() {
        storedToken("raw-expired", Instant.now().minus(Duration.ofMinutes(1)));

        assertThatThrownBy(() -> refreshTokenService.rotate("raw-expired"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Session has expired. Please log in again.");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotate_forDeactivatedEmployee_isRejectedAndTokenRevoked() {
        rakib.setActive(false);
        RefreshToken current = storedToken("raw-inactive", Instant.now().plus(Duration.ofDays(1)));

        assertThatThrownBy(() -> refreshTokenService.rotate("raw-inactive"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("deactivated");
        assertThat(current.getRevokedReason()).isEqualTo(RevokedReason.ACCOUNT_DEACTIVATED);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revoke_marksTokenRevoked_andIgnoresUnknownTokens() {
        RefreshToken current = storedToken("raw-logout", Instant.now().plus(Duration.ofDays(1)));
        when(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash("unknown"))).thenReturn(Optional.empty());

        refreshTokenService.revoke("raw-logout");
        refreshTokenService.revoke("unknown");

        assertThat(current.getRevokedReason()).isEqualTo(RevokedReason.LOGOUT);
    }

    @Test
    void revokeAllForEmployee_delegatesToBulkUpdateWithReason() {
        refreshTokenService.revokeAllForEmployee(7L, RevokedReason.PASSWORD_CHANGED);

        verify(refreshTokenRepository)
                .revokeAllActiveByEmployeeId(eq(7L), any(Instant.class), eq(RevokedReason.PASSWORD_CHANGED));
    }

    @Test
    void hash_isDeterministicSha256Hex() {
        // SHA-256("abc") is a well-known test vector.
        assertThat(RefreshTokenService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    private RefreshToken storedToken(String raw, Instant expiresAt) {
        RefreshToken token = new RefreshToken(rakib, RefreshTokenService.hash(raw), expiresAt);
        when(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(raw))).thenReturn(Optional.of(token));
        return token;
    }
}
