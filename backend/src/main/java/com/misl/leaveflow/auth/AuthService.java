package com.misl.leaveflow.auth;

import com.misl.leaveflow.auth.dto.ChangePasswordRequest;
import com.misl.leaveflow.auth.dto.CurrentUserResponse;
import com.misl.leaveflow.auth.dto.LoginRequest;
import com.misl.leaveflow.auth.dto.LoginResponse;
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

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Verifies the credentials and issues a JWT.
     * A wrong email/password makes AuthenticationManager throw BadCredentialsException,
     * and a deactivated account throws DisabledException. Both are turned into 401 by GlobalExceptionHandler.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        EmployeeUserDetails user = (EmployeeUserDetails) authentication.getPrincipal();
        JwtService.IssuedToken issued = jwtService.generateToken(user);

        return new LoginResponse(issued.token(), "Bearer", issued.expiresAt(), getCurrentUser(user.getId()));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(Long employeeId) {
        return CurrentUserResponse.from(findEmployee(employeeId));
    }

    @Transactional
    public void changePassword(Long employeeId, ChangePasswordRequest request) {
        Employee employee = findEmployee(employeeId);

        if (!passwordEncoder.matches(request.currentPassword(), employee.getPasswordHash())) {
            throw new BusinessRuleException("Current password is incorrect");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessRuleException("New password must be different from the current password");
        }

        employee.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    private Employee findEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
    }
}
