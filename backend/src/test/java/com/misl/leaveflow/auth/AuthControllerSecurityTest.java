package com.misl.leaveflow.auth;

import com.misl.leaveflow.auth.dto.AuthResponse;
import com.misl.leaveflow.auth.dto.CurrentUserResponse;
import com.misl.leaveflow.auth.refresh.InvalidRefreshTokenException;
import com.misl.leaveflow.auth.refresh.RefreshTokenCookieFactory;
import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.Role;
import com.misl.leaveflow.security.EmployeeUserDetails;
import com.misl.leaveflow.security.EmployeeUserDetailsService;
import com.misl.leaveflow.security.JwtProperties;
import com.misl.leaveflow.security.JwtService;
import com.misl.leaveflow.security.SecurityConfig;
import com.misl.leaveflow.security.SecurityErrorHandler;
import com.misl.leaveflow.support.TestEmployees;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real security filter chain (JWT filter, entry point, access-denied handler, CORS)
 * and the cookie handling of AuthController, with AuthService mocked out.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtService.class, SecurityErrorHandler.class, RefreshTokenCookieFactory.class,
        AuthControllerSecurityTest.AdminOnlyController.class})
class AuthControllerSecurityTest {

    private static final String COOKIE = RefreshTokenCookieFactory.COOKIE_NAME;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JwtProperties jwtProperties;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private EmployeeUserDetailsService userDetailsService;

    private final Employee admin = TestEmployees.employee(1L, "admin@leaveflow.com", Role.ADMIN);
    private final Employee rakib = TestEmployees.employee(7L, "rakib@leaveflow.com", Role.EMPLOYEE);

    // ---------- login ----------

    @Test
    void login_returnsAccessTokenInBody_andRefreshTokenInSecureCookie() throws Exception {
        when(authService.login(any())).thenReturn(result("access-jwt", "raw-refresh"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"rakib@leaveflow.com\",\"password\":\"Password@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.role").value("EMPLOYEE"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString(COOKIE + "=raw-refresh"),
                        containsString("HttpOnly"),
                        containsString("SameSite=Strict"),
                        containsString("Path=/api/auth"),
                        containsString("Max-Age=604800"))));
    }

    @Test
    void login_withBadCredentials_returns401() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"rakib@leaveflow.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
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

    // ---------- refresh ----------

    @Test
    void refresh_isPublic_readsCookie_andRotatesIt() throws Exception {
        when(authService.refresh("raw-old")).thenReturn(result("new-jwt", "raw-new"));

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, "raw-old")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-jwt"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(COOKIE + "=raw-new")));
    }

    @Test
    void refresh_worksEvenWithExpiredAccessTokenInHeader() throws Exception {
        when(authService.refresh("raw-old")).thenReturn(result("new-jwt", "raw-new"));

        mockMvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredTokenFor(rakib))
                        .cookie(new Cookie(COOKIE, "raw-old")))
                .andExpect(status().isOk());
    }

    @Test
    void refresh_withInvalidToken_returns401AndClearsCookie() throws Exception {
        when(authService.refresh(eq(null)))
                .thenThrow(new InvalidRefreshTokenException("Refresh token is missing. Please log in."));

        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token is missing. Please log in."))
                .andExpect(jsonPath("$.path").value("/api/auth/refresh"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        allOf(containsString(COOKIE + "="), containsString("Max-Age=0"))));
    }

    // ---------- logout ----------

    @Test
    void logout_isPublic_revokesAndClearsCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie(COOKIE, "raw-refresh")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        org.mockito.Mockito.verify(authService).logout("raw-refresh");
    }

    // ---------- access token on protected endpoints ----------

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required. Please log in."));
    }

    @Test
    void protectedEndpoint_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required. Please log in."));
    }

    @Test
    void protectedEndpoint_withExpiredToken_tellsClientToRefresh() throws Exception {
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredTokenFor(rakib)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Access token has expired"));
    }

    @Test
    void protectedEndpoint_withValidToken_isAuthorisedWithoutAnyUserLookup() throws Exception {
        when(authService.getCurrentUser(7L)).thenReturn(CurrentUserResponse.from(rakib));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(rakib)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("rakib@leaveflow.com"));

        // Stateless: the filter trusts the signed token and never calls the user store.
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void refreshTokenUsedAsBearerToken_isRejected() throws Exception {
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer raw-refresh-token-value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_withEmployeeToken_returns403() throws Exception {
        mockMvc.perform(get("/api/test/admin-only").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(rakib)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action"));
    }

    @Test
    void adminEndpoint_withAdminToken_returns200() throws Exception {
        mockMvc.perform(get("/api/test/admin-only").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(admin)))
                .andExpect(status().isOk());
    }

    // ---------- change password ----------

    @Test
    void changePassword_withWeakPassword_returns400() throws Exception {
        mockMvc.perform(put("/api/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(rakib))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Password@123\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
    }

    @Test
    void changePassword_returns204WithFreshRefreshCookie() throws Exception {
        when(authService.changePassword(eq(7L), any())).thenReturn("raw-after-change");

        mockMvc.perform(put("/api/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(rakib))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Password@123\",\"newPassword\":\"NewPass123\"}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(COOKIE + "=raw-after-change")));
    }

    @Test
    void changePassword_withoutToken_returns401() throws Exception {
        mockMvc.perform(put("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Password@123\",\"newPassword\":\"NewPass123\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- CORS ----------

    @Test
    void corsPreflight_fromFrontendOrigin_allowsCredentials() throws Exception {
        mockMvc.perform(options("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void corsPreflight_fromUnknownOrigin_isRejected() throws Exception {
        mockMvc.perform(options("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private AuthService.AuthResult result(String accessToken, String refreshToken) {
        AuthResponse body = new AuthResponse(accessToken, "Bearer", Instant.now().plusSeconds(900),
                CurrentUserResponse.from(rakib));
        return new AuthService.AuthResult(body, refreshToken);
    }

    private String tokenFor(Employee employee) {
        return jwtService.generateAccessToken(EmployeeUserDetails.fromEmployee(employee)).token();
    }

    /** Signed with the application's real secret, but already expired. */
    private String expiredTokenFor(Employee employee) {
        JwtService expiring = new JwtService(new JwtProperties(jwtProperties.secret(), Duration.ofSeconds(-5),
                jwtProperties.refreshTokenExpiration(), false));
        return expiring.generateAccessToken(EmployeeUserDetails.fromEmployee(employee)).token();
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
