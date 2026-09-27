package com.misl.leaveflow.auth;

import com.misl.leaveflow.auth.dto.AuthResponse;
import com.misl.leaveflow.auth.dto.ChangePasswordRequest;
import com.misl.leaveflow.auth.dto.CurrentUserResponse;
import com.misl.leaveflow.auth.dto.LoginRequest;
import com.misl.leaveflow.auth.refresh.InvalidRefreshTokenException;
import com.misl.leaveflow.auth.refresh.RefreshTokenCookieFactory;
import com.misl.leaveflow.common.exception.ApiError;
import com.misl.leaveflow.security.EmployeeUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Token flow:
 * <pre>
 *  login    -> access token (JSON body) + refresh token (httpOnly cookie)
 *  API call -> "Authorization: Bearer &lt;access token&gt;"
 *  401 "Access token has expired" -> POST /refresh (cookie sent automatically) -> new pair
 *  logout   -> refresh token revoked + cookie cleared
 * </pre>
 */
@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieFactory cookieFactory;

    @Operation(summary = "Log in; returns an access token and sets the refresh-token cookie")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthResult result = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.create(result.refreshToken()).toString())
                .body(result.body());
    }

    @Operation(summary = "Get a new access token using the refresh-token cookie (the cookie is rotated)")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Parameter(hidden = true)
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        AuthService.AuthResult result = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.create(result.refreshToken()).toString())
                .body(result.body());
    }

    @Operation(summary = "Log out; revokes the refresh token and clears its cookie")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Parameter(hidden = true)
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    @Operation(summary = "Get the profile of the logged-in user")
    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal EmployeeUserDetails currentUser) {
        return authService.getCurrentUser(currentUser.getId());
    }

    @Operation(summary = "Change password; signs out all other sessions")
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal EmployeeUserDetails currentUser,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        String newRefreshToken = authService.changePassword(currentUser.getId(), request);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.create(newRefreshToken).toString())
                .build();
    }

    /** A failed refresh means the session is over: answer 401 and delete the useless cookie. */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> handleInvalidRefreshToken(InvalidRefreshTokenException ex,
                                                              HttpServletRequest request) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), ex.getMessage(),
                request.getRequestURI(), null);
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .body(body);
    }
}
