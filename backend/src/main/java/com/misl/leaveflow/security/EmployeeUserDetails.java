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
 * <p>
 * It is created in two ways:
 * <ul>
 *   <li>{@link #fromEmployee(Employee)}: at login, from the database row (includes the password hash to check).</li>
 *   <li>{@link #fromTokenClaims}: on every other request, from the verified access token alone (no database hit).</li>
 * </ul>
 */
public class EmployeeUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final String fullName;
    private final Role role;
    private final boolean active;

    private EmployeeUserDetails(Long id, String email, String passwordHash, String fullName, Role role,
                                boolean active) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.active = active;
    }

    public static EmployeeUserDetails fromEmployee(Employee employee) {
        return new EmployeeUserDetails(employee.getId(), employee.getEmail(), employee.getPasswordHash(),
                employee.getFullName(), employee.getRole(), employee.isActive());
    }

    /** A token is only issued to active users, so a valid token implies an active user. */
    public static EmployeeUserDetails fromTokenClaims(Long id, String email, String fullName, Role role) {
        return new EmployeeUserDetails(id, email, null, fullName, role, true);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
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
