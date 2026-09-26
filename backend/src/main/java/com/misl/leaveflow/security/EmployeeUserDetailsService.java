package com.misl.leaveflow.security;

import com.misl.leaveflow.employee.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Loads a user by email. Used in two places:
 * the login check (via AuthenticationManager) and the JWT filter on every request.
 */
@Service
@RequiredArgsConstructor
public class EmployeeUserDetailsService implements UserDetailsService {

    private final EmployeeRepository employeeRepository;

    @Override
    public EmployeeUserDetails loadUserByUsername(String email) {
        return employeeRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .map(EmployeeUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("No employee with email " + email));
    }
}
