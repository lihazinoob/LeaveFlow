package com.misl.leaveflow.auth;

import com.misl.leaveflow.auth.dto.CurrentUserResponse;
import com.misl.leaveflow.auth.dto.LoginResponse;
import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.Role;
import com.misl.leaveflow.security.EmployeeUserDetails;
import com.misl.leaveflow.security.EmployeeUserDetailsService;
import com.misl.leaveflow.security.JwtService;
import com.misl.leaveflow.security.SecurityConfig;
import com.misl.leaveflow.security.SecurityErrorHandler;
import com.misl.leaveflow.support.TestEmployees;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real security filter chain (JWT filter, entry point, access-denied handler)
 * with the service layer mocked out.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtService.class, SecurityErrorHandler.class,
        AuthControllerSecurityTest.AdminOnlyController.class})
class AuthControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private EmployeeUserDetailsService userDetailsService;

    private final Employee admin = TestEmployees.employee(1L, "admin@leaveflow.com", Role.ADMIN);
    private final Employee rakib = TestEmployees.employee(7L, "rakib@leaveflow.com", Role.EMPLOYEE);

    @Test
    void login_isPublicAndReturnsToken() throws Exception {
        CurrentUserResponse user = CurrentUserResponse.from(rakib);
        when(authService.login(any())).thenReturn(new LoginResponse("jwt-token", "Bearer", Instant.now(), user));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"rakib@leaveflow.com\",\"password\":\"Password@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.user.role").value("EMPLOYEE"));
    }

    @Test
    void login_withBadCredentials_returns401() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"rakib@leaveflow.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void login_withDeactivatedAccount_returns401WithReason() throws Exception {
        when(authService.login(any())).thenThrow(new DisabledException("User is disabled"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"rakib@leaveflow.com\",\"password\":\"Password@123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Your account has been deactivated. Please contact the administrator."));
    }

    @Test
    void login_withInvalidBody_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").value("Email must be a valid email address"))
                .andExpect(jsonPath("$.fieldErrors.password").value("Password is required"));
    }

    @Test
    void me_withoutToken_returns401Json() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required. Please log in."));
    }

    @Test
    void me_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withValidToken_returnsProfile() throws Exception {
        String token = tokenFor(rakib);
        when(authService.getCurrentUser(7L)).thenReturn(CurrentUserResponse.from(rakib));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("rakib@leaveflow.com"));
    }

    @Test
    void validToken_ofDeactivatedUser_isRejected() throws Exception {
        String token = tokenFor(rakib);
        rakib.setActive(false);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_withEmployeeToken_returns403() throws Exception {
        mockMvc.perform(get("/api/test/admin-only").header("Authorization", "Bearer " + tokenFor(rakib)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action"));
    }

    @Test
    void adminEndpoint_withAdminToken_returns200() throws Exception {
        mockMvc.perform(get("/api/test/admin-only").header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_withWeakPassword_returns400() throws Exception {
        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + tokenFor(rakib))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Password@123\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
    }

    @Test
    void changePassword_withValidRequest_returns204() throws Exception {
        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + tokenFor(rakib))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Password@123\",\"newPassword\":\"NewPass123\"}"))
                .andExpect(status().isNoContent());
    }

    /** Issues a real token and makes the (mocked) user lookup return the given employee. */
    private String tokenFor(Employee employee) {
        when(userDetailsService.loadUserByUsername(employee.getEmail()))
                .thenAnswer(invocation -> new EmployeeUserDetails(employee));
        return jwtService.generateToken(new EmployeeUserDetails(employee)).token();
    }

    @RestController
    static class AdminOnlyController {

        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/api/test/admin-only")
        String adminOnly() {
            return "ok";
        }
    }
}
