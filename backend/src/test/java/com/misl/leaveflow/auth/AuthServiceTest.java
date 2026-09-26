package com.misl.leaveflow.auth;

import com.misl.leaveflow.auth.dto.ChangePasswordRequest;
import com.misl.leaveflow.auth.dto.LoginRequest;
import com.misl.leaveflow.auth.dto.LoginResponse;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private final Employee rakib = TestEmployees.employee(7L, "rakib@leaveflow.com", Role.EMPLOYEE);

    @Test
    void login_normalisesEmailAndReturnsToken() {
        EmployeeUserDetails principal = new EmployeeUserDetails(rakib);
        Authentication authenticated =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        ArgumentCaptor<Authentication> attempt = ArgumentCaptor.forClass(Authentication.class);
        when(authenticationManager.authenticate(attempt.capture())).thenReturn(authenticated);
        when(jwtService.generateToken(principal)).thenReturn(new JwtService.IssuedToken("jwt-token", Instant.now()));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(rakib));

        LoginResponse response = authService.login(new LoginRequest("  Rakib@LeaveFlow.com ", "Password@123"));

        assertThat(attempt.getValue().getName()).isEqualTo("rakib@leaveflow.com");
        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().email()).isEqualTo("rakib@leaveflow.com");
        assertThat(response.user().role()).isEqualTo(Role.EMPLOYEE);
    }

    @Test
    void login_withWrongPassword_propagatesBadCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("rakib@leaveflow.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void changePassword_withWrongCurrentPassword_isRejected() {
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(rakib));
        when(passwordEncoder.matches("wrong", rakib.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(7L, new ChangePasswordRequest("wrong", "NewPass123")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Current password is incorrect");
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
    void changePassword_storesNewHash() {
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(rakib));
        when(passwordEncoder.matches("Password@123", rakib.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("NewPass123")).thenReturn("$2a$10$newhash");

        authService.changePassword(7L, new ChangePasswordRequest("Password@123", "NewPass123"));

        assertThat(rakib.getPasswordHash()).isEqualTo("$2a$10$newhash");
    }
}
