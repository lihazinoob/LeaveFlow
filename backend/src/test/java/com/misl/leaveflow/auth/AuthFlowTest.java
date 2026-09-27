package com.misl.leaveflow.auth;

import com.jayway.jsonpath.JsonPath;
import com.misl.leaveflow.auth.refresh.RefreshToken;
import com.misl.leaveflow.auth.refresh.RefreshTokenCookieFactory;
import com.misl.leaveflow.auth.refresh.RefreshTokenRepository;
import com.misl.leaveflow.auth.refresh.RefreshTokenService;
import com.misl.leaveflow.auth.refresh.RevokedReason;
import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.EmployeeRepository;
import com.misl.leaveflow.employee.Role;
import com.misl.leaveflow.security.EmployeeUserDetailsService;
import com.misl.leaveflow.security.JwtService;
import com.misl.leaveflow.security.SecurityConfig;
import com.misl.leaveflow.security.SecurityErrorHandler;
import com.misl.leaveflow.support.TestEmployees;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the whole authentication pipeline through HTTP:
 * login -> call API with access token -> refresh (rotation) -> reuse detection -> logout -> password change.
 * <p>
 * Every class involved is the real one (controller, AuthService, RefreshTokenService, JwtService,
 * AuthenticationManager with BCrypt, the security filter chain). Only the two repositories are
 * replaced by in-memory maps, so the test needs no database.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtService.class, SecurityErrorHandler.class, RefreshTokenCookieFactory.class,
        AuthService.class, RefreshTokenService.class, EmployeeUserDetailsService.class})
class AuthFlowTest {

    private static final String COOKIE = RefreshTokenCookieFactory.COOKIE_NAME;
    private static final String PASSWORD = "Password@123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeRepository employeeRepository;
    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    private final Map<String, RefreshToken> refreshTokenTable = new HashMap<>();
    private Employee rakib;

    @BeforeEach
    void setUp() {
        rakib = TestEmployees.employee(7L, "rakib@leaveflow.com", Role.EMPLOYEE);
        rakib.setPasswordHash(new BCryptPasswordEncoder().encode(PASSWORD));

        when(employeeRepository.findByEmail("rakib@leaveflow.com")).thenAnswer(inv -> Optional.of(rakib));
        when(employeeRepository.findById(7L)).thenAnswer(inv -> Optional.of(rakib));

        // In-memory stand-in for the refresh_tokens table.
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken token = inv.getArgument(0);
            refreshTokenTable.put(token.getTokenHash(), token);
            return token;
        });
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(refreshTokenTable.get(inv.<String>getArgument(0))));
        when(refreshTokenRepository.revokeAllActiveByEmployeeId(anyLong(), any(Instant.class), any(RevokedReason.class)))
                .thenAnswer(inv -> revokeAllInTable(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
    }

    @Test
    void fullSession_loginCallRefreshAndReuseDetection() throws Exception {
        Session first = login("Rakib@LeaveFlow.com", PASSWORD);
        assertThat(refreshTokenTable).hasSize(1);
        assertThat(refreshTokenTable).doesNotContainKey(first.refreshToken); // only the hash is stored

        // 1. The access token opens protected endpoints.
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + first.accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("rakib@leaveflow.com"));

        // 2. Refresh returns a brand-new access token and rotates the refresh token.
        Session second = refresh(first.refreshToken);
        assertThat(second.refreshToken).isNotEqualTo(first.refreshToken);
        assertThat(second.accessToken).isNotBlank();
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + second.accessToken))
                .andExpect(status().isOk());

        // 3. Replaying the old (rotated) refresh token is treated as theft...
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, first.refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token has already been used. Please log in again."));

        // 4. ...so every session is revoked, including the legitimate newest one.
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, second.refreshToken)))
                .andExpect(status().isUnauthorized());
        assertThat(refreshTokenTable.values()).allMatch(RefreshToken::isRevoked);
    }

    @Test
    void logout_endsOnlyThatSession() throws Exception {
        Session laptop = login("rakib@leaveflow.com", PASSWORD);
        Session phone = login("rakib@leaveflow.com", PASSWORD);

        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie(COOKIE, laptop.refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, laptop.refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Session has ended. Please log in again."));
        // A logged-out token coming back is not theft, so the other device stays signed in.
        refresh(phone.refreshToken);
    }

    @Test
    void refresh_withForgedOrMissingCookie_isRejected() throws Exception {
        login("rakib@leaveflow.com", PASSWORD);

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, "forged-token-value")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid refresh token. Please log in again."));
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token is missing. Please log in."));
    }

    @Test
    void changePassword_signsOutOtherSessions_butKeepsCurrentOne() throws Exception {
        Session laptop = login("rakib@leaveflow.com", PASSWORD);
        Session phone = login("rakib@leaveflow.com", PASSWORD);

        MockHttpServletResponse response = mockMvc.perform(put("/api/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + laptop.accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Password@123\",\"newPassword\":\"NewPass123\"}"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();
        String laptopNewRefreshToken = response.getCookie(COOKIE).getValue();

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, phone.refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Session has ended. Please log in again."));
        // The phone's attempt must not be mistaken for theft and kill the laptop's new session.
        refresh(laptopNewRefreshToken);

        loginExpecting("rakib@leaveflow.com", PASSWORD, 401);
        login("rakib@leaveflow.com", "NewPass123");
    }

    @Test
    void deactivatedEmployee_cannotRefreshOrLogIn() throws Exception {
        Session session = login("rakib@leaveflow.com", PASSWORD);
        rakib.setActive(false);

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, session.refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Your account has been deactivated. Please contact the administrator."));
        loginExpecting("rakib@leaveflow.com", PASSWORD, 401);
    }

    @Test
    void wrongPasswordAndUnknownEmail_getTheSameAnswer() throws Exception {
        String wrongPassword = loginExpecting("rakib@leaveflow.com", "WrongPass1", 401);
        String unknownEmail = loginExpecting("ghost@leaveflow.com", PASSWORD, 401);

        assertThat(JsonPath.<String>read(wrongPassword, "$.message")).isEqualTo("Invalid email or password");
        assertThat(JsonPath.<String>read(unknownEmail, "$.message")).isEqualTo("Invalid email or password");
        assertThat(refreshTokenTable).isEmpty();
    }

    // ---------- helpers ----------

    private int revokeAllInTable(Long employeeId, Instant now, RevokedReason reason) {
        int revoked = 0;
        for (RefreshToken token : refreshTokenTable.values()) {
            if (token.getEmployee().getId().equals(employeeId) && !token.isRevoked()) {
                token.revoke(now, reason);
                revoked++;
            }
        }
        return revoked;
    }

    private record Session(String accessToken, String refreshToken) {
    }

    private Session login(String email, String password) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        return toSession(response);
    }

    private String loginExpecting(String email, String password, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private Session refresh(String refreshToken) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(COOKIE, refreshToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        return toSession(response);
    }

    private Session toSession(MockHttpServletResponse response) throws Exception {
        String accessToken = JsonPath.read(response.getContentAsString(), "$.accessToken");
        Cookie cookie = response.getCookie(COOKIE);
        assertThat(cookie).as("refresh token cookie").isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        return new Session(accessToken, cookie.getValue());
    }
}
