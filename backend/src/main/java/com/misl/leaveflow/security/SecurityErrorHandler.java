package com.misl.leaveflow.security;

import com.misl.leaveflow.common.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;

/**
 * Writes 401/403 responses produced inside the security filter chain (before any controller runs)
 * in the same {@link ApiError} JSON shape used by GlobalExceptionHandler.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /** No valid token was sent to a protected endpoint. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, request, HttpStatus.UNAUTHORIZED, "Authentication required. Please log in.");
    }

    /** A valid token was sent, but the user's role is not allowed to access the URL. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, request, HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
    }

    private void write(HttpServletResponse response, HttpServletRequest request, HttpStatus status,
                       String message) throws IOException {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message,
                request.getRequestURI(), null);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
