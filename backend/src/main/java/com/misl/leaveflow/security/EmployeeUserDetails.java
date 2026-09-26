package com.misl.leaveflow.security;

import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The logged-in user as Spring Security sees it. Controllers receive it through
 * {@code @AuthenticationPrincipal} to know who is calling.
 */
public class EmployeeUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final String fullName;
    private final Role role;
    private final boolean active;

    public EmployeeUserDetails(Employee employee) {
        this.id = employee.getId();
        this.email = employee.getEmail();
        this.passwordHash = employee.getPasswordHash();
        this.fullName = employee.getFullName();
        this.role = employee.getRole();
        this.active = employee.isActive();
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    /** Spring's hasRole('ADMIN') checks for the authority "ROLE_ADMIN". */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
