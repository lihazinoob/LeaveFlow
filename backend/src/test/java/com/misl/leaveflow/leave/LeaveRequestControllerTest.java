package com.misl.leaveflow.leave;

import com.misl.leaveflow.common.config.ClockConfig;
import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.Role;
import com.misl.leaveflow.holiday.PublicHolidayController;
import com.misl.leaveflow.holiday.PublicHolidayService;
import com.misl.leaveflow.leave.dto.LeavePreviewResponse;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Role rules, request binding and validation of the leave and holiday endpoints (services mocked). */
@WebMvcTest(controllers = {LeaveRequestController.class, PublicHolidayController.class})
@Import({SecurityConfig.class, JwtService.class, SecurityErrorHandler.class, ClockConfig.class})
class LeaveRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private LeaveRequestService leaveRequestService;
    @MockitoBean
    private LeaveWorkflowService workflowService;
    @MockitoBean
    private LeaveBalanceService balanceService;
    @MockitoBean
    private PublicHolidayService holidayService;
    @MockitoBean
    private EmployeeUserDetailsService userDetailsService;

    private final Employee admin = TestEmployees.employee(1L, "admin@leaveflow.com", Role.ADMIN);
    private final Employee manager = TestEmployees.employee(2L, "tanvir@leaveflow.com", Role.MANAGER);
    private final Employee rakib = TestEmployees.employee(7L, "rakib@leaveflow.com", Role.EMPLOYEE);

    @Test
    void apply_withMissingFields_returnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/leaves").header(HttpHeaders.AUTHORIZATION, bearer(rakib))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.leaveTypeId").value("Leave type is required"))
                .andExpect(jsonPath("$.fieldErrors.startDate").value("Start date is required"))
                .andExpect(jsonPath("$.fieldErrors.reason").value("Reason is required"));
    }

    @Test
    void preview_bindsIsoDates() throws Exception {
        when(leaveRequestService.preview(any(), eq(1L), eq(LocalDate.of(2026, 10, 4)), eq(LocalDate.of(2026, 10, 8)),
                isNull())).thenReturn(LeavePreviewResponse.of(5, 15, List.of()));

        mockMvc.perform(get("/api/leaves/preview").header(HttpHeaders.AUTHORIZATION, bearer(rakib))
                        .param("leaveTypeId", "1").param("startDate", "2026-10-04").param("endDate", "2026-10-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workingDays").value(5))
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void preview_withBadDate_is400() throws Exception {
        mockMvc.perform(get("/api/leaves/preview").header(HttpHeaders.AUTHORIZATION, bearer(rakib))
                        .param("leaveTypeId", "1").param("startDate", "04/10/2026").param("endDate", "2026-10-08"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approveAndReject_byEmployee_are403_evenWithInvalidBody() throws Exception {
        String tooLong = "x".repeat(600);
        mockMvc.perform(post("/api/leaves/5/approve").header(HttpHeaders.AUTHORIZATION, bearer(rakib))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"" + tooLong + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/leaves/5/reject").header(HttpHeaders.AUTHORIZATION, bearer(rakib)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(workflowService);
    }

    @Test
    void approve_byManager_withoutBody_passesNullComment() throws Exception {
        mockMvc.perform(post("/api/leaves/5/approve").header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk());
        verify(workflowService).approve(any(EmployeeUserDetails.class), eq(5L), isNull());
    }

    @Test
    void reject_withTooLongComment_is400() throws Exception {
        mockMvc.perform(post("/api/leaves/5/reject").header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.comment").exists());
    }

    @Test
    void cancel_isOpenToEmployees() throws Exception {
        mockMvc.perform(post("/api/leaves/5/cancel").header(HttpHeaders.AUTHORIZATION, bearer(rakib)))
                .andExpect(status().isOk());
        verify(workflowService).cancel(any(EmployeeUserDetails.class), eq(5L), isNull());
    }

    @Test
    void approvalsTeamAndAllLeaves_areRoleRestricted() throws Exception {
        mockMvc.perform(get("/api/leaves/approvals").header(HttpHeaders.AUTHORIZATION, bearer(rakib)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/leaves/team").header(HttpHeaders.AUTHORIZATION, bearer(rakib)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/leaves").header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/leaves/approvals").header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk());
    }

    @Test
    void myLeaves_withInvalidStatus_is400() throws Exception {
        mockMvc.perform(get("/api/leaves/my").param("status", "DONE").header(HttpHeaders.AUTHORIZATION, bearer(rakib)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void holidays_readableByAll_writableByAdminOnly() throws Exception {
        mockMvc.perform(get("/api/holidays").param("year", "2026").header(HttpHeaders.AUTHORIZATION, bearer(rakib)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/holidays").header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/holidays").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-11-01\",\"name\":\"Company Day\"}"))
                .andExpect(status().isCreated());
        verify(holidayService).findByYear(2026);
    }

    private String bearer(Employee employee) {
        return "Bearer " + jwtService.generateAccessToken(EmployeeUserDetails.fromEmployee(employee)).token();
    }
}
