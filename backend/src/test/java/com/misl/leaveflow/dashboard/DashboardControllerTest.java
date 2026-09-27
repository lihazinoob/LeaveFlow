package com.misl.leaveflow.dashboard;

import com.misl.leaveflow.common.config.ClockConfig;
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
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.YearMonth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DashboardController.class)
@Import({SecurityConfig.class, JwtService.class, SecurityErrorHandler.class, ClockConfig.class})
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private DashboardService dashboardService;
    @MockitoBean
    private CalendarService calendarService;
    @MockitoBean
    private EmployeeUserDetailsService userDetailsService;

    private final Employee admin = TestEmployees.employee(1L, "admin@leaveflow.com", Role.ADMIN);
    private final Employee manager = TestEmployees.employee(2L, "tanvir@leaveflow.com", Role.MANAGER);
    private final Employee rakib = TestEmployees.employee(7L, "rakib@leaveflow.com", Role.EMPLOYEE);

    @Test
    void myDashboard_isForEveryone() throws Exception {
        mockMvc.perform(get("/api/dashboard/me").header(HttpHeaders.AUTHORIZATION, bearer(rakib)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void teamDashboard_isForManagersAndAdmins() throws Exception {
        mockMvc.perform(get("/api/dashboard/team").header(HttpHeaders.AUTHORIZATION, bearer(rakib)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard/team").header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk());
    }

    @Test
    void organisationDashboard_isForAdminsOnly() throws Exception {
        mockMvc.perform(get("/api/dashboard/organisation").header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard/organisation").param("year", "2025")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
        verify(dashboardService).getOrganisationDashboard(2025);
    }

    @Test
    void calendar_bindsYearMonth_andDefaultsToCurrentMonth() throws Exception {
        mockMvc.perform(get("/api/calendar").param("month", "2026-10").header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk());
        verify(calendarService).getMonth(any(EmployeeUserDetails.class), eq(YearMonth.of(2026, 10)));

        mockMvc.perform(get("/api/calendar").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void calendar_withInvalidMonth_is400_andEmployeesAreForbidden() throws Exception {
        mockMvc.perform(get("/api/calendar").param("month", "2026-13").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/calendar").header(HttpHeaders.AUTHORIZATION, bearer(rakib)))
                .andExpect(status().isForbidden());
    }

    private String bearer(Employee employee) {
        return "Bearer " + jwtService.generateAccessToken(EmployeeUserDetails.fromEmployee(employee)).token();
    }
}
