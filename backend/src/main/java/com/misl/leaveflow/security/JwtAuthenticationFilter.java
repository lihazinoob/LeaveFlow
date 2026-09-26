package com.misl.leaveflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request, before Spring Security's authorization checks.
 * <ol>
 *   <li>Reads the "Authorization: Bearer &lt;token&gt;" header.</li>
 *   <li>Verifies the token signature and expiry.</li>
 *   <li>Loads the employee (so deactivated users are rejected immediately) and stores
 *       them in the SecurityContext for the rest of the request.</li>
 * </ol>
 * If anything is missing or invalid, the request simply continues unauthenticated,
 * and protected endpoints then answer 401.
 * <p>
 * Not annotated with @Component on purpose: it is added only to the security filter chain
 * in {@link SecurityConfig}, not registered a second time as a plain servlet filter.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final EmployeeUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            jwtService.extractValidSubject(token).ifPresent(email -> authenticate(email, request));
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String email, HttpServletRequest request) {
        try {
            EmployeeUserDetails user = userDetailsService.loadUserByUsername(email);
            if (!user.isEnabled()) {
                return;
            }
            var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (UsernameNotFoundException ex) {
            // The employee was removed after the token was issued: treat the request as anonymous.
        }
    }
}
