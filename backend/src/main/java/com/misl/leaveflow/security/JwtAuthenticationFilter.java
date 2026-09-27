package com.misl.leaveflow.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request, before Spring Security's authorization checks.
 * <ol>
 *   <li>Reads the "Authorization: Bearer &lt;access token&gt;" header.</li>
 *   <li>Verifies the token's signature and expiry (no database call; the token is self-contained).</li>
 *   <li>Stores the user from the token claims in the SecurityContext for the rest of the request.</li>
 * </ol>
 * If the header is missing or the token is invalid, the request continues unauthenticated,
 * and protected endpoints then answer 401. For an expired token, a request attribute is set so that
 * the 401 message tells the client to use its refresh token.
 * <p>
 * Not annotated with @Component on purpose: it is added only to the security filter chain
 * in {@link SecurityConfig}, not registered a second time as a plain servlet filter.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String TOKEN_EXPIRED_ATTRIBUTE = "leaveflow.accessTokenExpired";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                EmployeeUserDetails user = jwtService.parseAccessToken(token);
                var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ExpiredJwtException ex) {
                request.setAttribute(TOKEN_EXPIRED_ATTRIBUTE, Boolean.TRUE);
            } catch (JwtException ex) {
                // Invalid token: leave the request unauthenticated.
            }
        }

        filterChain.doFilter(request, response);
    }
}
