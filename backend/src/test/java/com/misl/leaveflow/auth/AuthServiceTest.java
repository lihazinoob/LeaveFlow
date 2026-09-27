package com.misl.leaveflow.auth;

import com.misl.leaveflow.auth.dto.ChangePasswordRequest;
import com.misl.leaveflow.auth.dto.LoginRequest;
import com.misl.leaveflow.auth.refresh.InvalidRefreshTokenException;
import com.misl.leaveflow.auth.refresh.RefreshTokenService;
import com.misl.leaveflow.auth.refresh.RevokedReason;
import com.misl.leaveflow.common.exception.BusinessRuleException;
import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.EmployeeRepository;
import com.misl.leaveflow.employee.Role;
import com.misl.leaveflow.security.EmployeeUserDetails;
import com.misl.leaveflow.security.JwtService;
import com.misl.leaveflow.support.TestEmployees;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private final Employee rakib = TestEmployees.employee(7L, "rakib@leaveflow.com", Role.EMPLOYEE);

    @Test
    void login_normalisesEmail_andReturnsAccessAndRefreshTokens() {
        EmployeeUserDetails principal = EmployeeUserDetails.fromEmployee(rakib);
        Authentication authenticated =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        ArgumentCaptor<Authentication> attempt = ArgumentCaptor.forClass(Authentication.class);
        when(authenticationManager.authenticate(attempt.capture())).thenReturn(authenticated);
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(rakib));
        when(refreshTokenService.issue(rakib)).thenReturn("raw-refresh");
        when(jwtService.generateAccessToken(any())).thenReturn(new JwtService.AccessToken("access-jwt", Instant.now()));

        AuthService.AuthResult result = authService.login(new LoginRequest("  Rakib@LeaveFlow.com ", "Password@123"));

        assertThat(attempt.getValue().getName()).isEqualTo("rakib@leaveflow.com");
        assertThat(result.body().accessToken()).isEqualTo("access-jwt");
        assertThat(result.body().tokenType()).isEqualTo("Bearer");
        assertThat(result.body().user().email()).isEqualTo("rakib@leaveflow.com");
        assertThat(result.refreshToken()).isEqualTo("raw-refresh");
    }

    @Test
    void login_withWrongPassword_issuesNoTokens() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("rakib@leaveflow.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(jwtService, refreshTokenService);
    }

    @Test
    void refresh_returnsNewAccessTokenAndRotatedRefreshToken() {
        when(refreshTokenService.rotate("raw-old")).thenReturn(new RefreshTokenService.Rotation(rakib, "raw-new"));
        when(jwtService.generateAccessToken(any())).thenReturn(new JwtService.AccessToken("new-jwt", Instant.now()));

        AuthService.AuthResult result = authService.refresh("raw-old");

        assertThat(result.body().accessToken()).isEqualTo("new-jwt");
        assertThat(result.body().user().id()).isEqualTo(7L);
        assertThat(result.refreshToken()).isEqualTo("raw-new");
    }

    @Test
    void refresh_withInvalidToken_propagatesAndIssuesNoAccessToken() {
        when(refreshTokenService.rotate("bad")).thenThrow(new InvalidRefreshTokenException("Invalid"));

        assertThatThrownBy(() -> authService.refresh("bad")).isInstanceOf(InvalidRefreshTokenException.class);
        verifyNoInteractions(jwtService);
    }

    @Test
    void logout_revokesToken_andIgnoresMissingCookie() {
        authService.logout("raw-refresh");
        authService.logout(null);
        authService.logout("");

        verify(refreshTokenService).revoke("raw-refresh");
        verify(refreshTokenService, never()).revoke(null);
        verify(refreshTokenService, never()).revoke("");
    }

    @Test
    void changePassword_withWrongCurrentPassword_isRejected() {
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(rakib));
        when(passwordEncoder.matches("wrong", rakib.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(7L, new ChangePasswordRequest("wrong", "NewPass123")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Current password is incorrect");
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void changePassword_toSamePassword_isRejected() {
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(rakib));
        when(passwordEncoder.matches("Password@123", rakib.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(7L,
                new ChangePasswordRequest("Password@123", "Password@123")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("New password must be different from the current password");
    }

    @Test
    void changePassword_storesNewHash_revokesAllSessions_andIssuesNewRefreshToken() {
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(rakib));
        when(passwordEncoder.matches("Password@123", rakib.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("NewPass123")).thenReturn("$2a$10$newhash");
        when(refreshTokenService.issue(rakib)).thenReturn("raw-after-change");

        String newRefreshToken = authService.changePassword(7L, new ChangePasswordRequest("Password@123", "NewPass123"));

        assertThat(rakib.getPasswordHash()).isEqualTo("$2a$10$newhash");
        verify(refreshTokenService).revokeAllForEmployee(7L, RevokedReason.PASSWORD_CHANGED);
        assertThat(newRefreshToken).isEqualTo("raw-after-change");
    }
}
