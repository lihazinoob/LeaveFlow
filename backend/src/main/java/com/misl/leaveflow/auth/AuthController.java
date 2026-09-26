package com.misl.leaveflow.auth;

import com.misl.leaveflow.auth.dto.ChangePasswordRequest;
import com.misl.leaveflow.auth.dto.CurrentUserResponse;
import com.misl.leaveflow.auth.dto.LoginRequest;
import com.misl.leaveflow.auth.dto.LoginResponse;
import com.misl.leaveflow.security.EmployeeUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Log in with email and password and receive a JWT")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Get the profile of the logged-in user")
    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal EmployeeUserDetails currentUser) {
        return authService.getCurrentUser(currentUser.getId());
    }

    @Operation(summary = "Change the logged-in user's password")
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal EmployeeUserDetails currentUser,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(currentUser.getId(), request);
        return ResponseEntity.noContent().build();
    }
}
