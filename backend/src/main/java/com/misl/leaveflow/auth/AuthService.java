package com.misl.leaveflow.auth;

import com.misl.leaveflow.auth.dto.AuthResponse;
import com.misl.leaveflow.auth.dto.ChangePasswordRequest;
import com.misl.leaveflow.auth.dto.CurrentUserResponse;
import com.misl.leaveflow.auth.dto.LoginRequest;
import com.misl.leaveflow.auth.refresh.InvalidRefreshTokenException;
import com.misl.leaveflow.auth.refresh.RefreshTokenService;
import com.misl.leaveflow.auth.refresh.RevokedReason;
import com.misl.leaveflow.common.exception.BusinessRuleException;
import com.misl.leaveflow.common.exception.ResourceNotFoundException;
import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.EmployeeRepository;
import com.misl.leaveflow.security.EmployeeUserDetails;
import com.misl.leaveflow.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * The authentication use cases: login, refresh, logout, current user and change password.
 * Each successful login or refresh returns a new short-lived access token plus a new refresh token.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Verifies the credentials and starts a session.
     * A wrong email/password makes AuthenticationManager throw BadCredentialsException,
     * and a deactivated account throws DisabledException. Both are turned into 401 by GlobalExceptionHandler.
     */
    @Transactional
    public AuthResult login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        EmployeeUserDetails principal = (EmployeeUserDetails) authentication.getPrincipal();
        Employee employee = findEmployee(principal.getId());
        return buildResult(employee, refreshTokenService.issue(employee));
    }

    /** Exchanges a valid refresh token for a new access token, rotating the refresh token as well. */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResult refresh(String rawRefreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken);
        return buildResult(rotation.employee(), rotation.newRefreshToken());
    }

    /**
     * Ends the session by revoking its refresh token. The access token simply expires
     * within minutes; the frontend drops it from memory straight away.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revoke(rawRefreshToken);
        }
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(Long employeeId) {
        return CurrentUserResponse.from(findEmployee(employeeId));
    }

    /**
     * Changes the password and signs the user out of every other session (revokes all refresh tokens).
     *
     * @return a fresh refresh token for the current session, so the user making the change stays logged in
     */
    @Transactional
    public String changePassword(Long employeeId, ChangePasswordRequest request) {
        Employee employee = findEmployee(employeeId);

        if (!passwordEncoder.matches(request.currentPassword(), employee.getPasswordHash())) {
            throw new BusinessRuleException("Current password is incorrect");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessRuleException("New password must be different from the current password");
        }

        employee.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAllForEmployee(employeeId, RevokedReason.PASSWORD_CHANGED);
        return refreshTokenService.issue(employee);
    }

    private AuthResult buildResult(Employee employee, String rawRefreshToken) {
        JwtService.AccessToken accessToken = jwtService.generateAccessToken(EmployeeUserDetails.fromEmployee(employee));
        AuthResponse body = new AuthResponse(accessToken.token(), "Bearer", accessToken.expiresAt(),
                CurrentUserResponse.from(employee));
        return new AuthResult(body, rawRefreshToken);
    }

    private Employee findEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
    }

    /** What the controller needs: the JSON body, plus the raw refresh token to put in the cookie. */
    public record AuthResult(AuthResponse body, String refreshToken) {
    }
}
