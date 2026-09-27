package com.misl.leaveflow.employee;

import com.misl.leaveflow.common.dto.PageResponse;
import com.misl.leaveflow.employee.dto.EmployeeResponse;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Role-based access and request validation for the employee endpoints (service mocked). */
@WebMvcTest(controllers = EmployeeController.class)
@Import({SecurityConfig.class, JwtService.class, SecurityErrorHandler.class})
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private EmployeeService employeeService;
    @MockitoBean
    private EmployeeUserDetailsService userDetailsService;

    private final Employee admin = TestEmployees.employee(1L, "admin@leaveflow.com", Role.ADMIN);
    private final Employee manager = TestEmployees.employee(2L, "tanvir@leaveflow.com", Role.MANAGER);
    private final Employee rakib = TestEmployees.employee(7L, "rakib@leaveflow.com", Role.EMPLOYEE);

    @Test
    void search_asAdmin_returnsPageWithFiltersAndSorting() throws Exception {
        when(employeeService.search(eq("rak"), isNull(), eq(Role.EMPLOYEE), eq(true), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(EmployeeResponse.from(rakib)), 0, 5, 1, 1));

        mockMvc.perform(get("/api/employees")
                        .param("search", "rak").param("role", "EMPLOYEE").param("active", "true")
                        .param("page", "0").param("size", "5").param("sort", "email,desc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("rakib@leaveflow.com"))
                .andExpect(jsonPath("$.totalElements").value(1));

        var pageable = forClass(Pageable.class);
        verify(employeeService).search(eq("rak"), isNull(), eq(Role.EMPLOYEE), eq(true), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort().getOrderFor("email").isDescending()).isTrue();
    }

    @Test
    void search_asEmployeeOrManager_isForbidden() throws Exception {
        mockMvc.perform(get("/api/employees").header(HttpHeaders.AUTHORIZATION, bearer(rakib)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employees").header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(employeeService);
    }

    @Test
    void search_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/employees")).andExpect(status().isUnauthorized());
    }

    @Test
    void search_withInvalidRoleFilter_is400() throws Exception {
        mockMvc.perform(get("/api/employees").param("role", "CEO").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'CEO' for parameter 'role'"));
    }

    @Test
    void team_asManager_returnsDirectReports() throws Exception {
        when(employeeService.getTeam(2L)).thenReturn(List.of(EmployeeResponse.from(rakib)));

        mockMvc.perform(get("/api/employees/team").header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value(rakib.getFullName()));
    }

    @Test
    void team_asEmployee_isForbidden() throws Exception {
        mockMvc.perform(get("/api/employees/team").header(HttpHeaders.AUTHORIZATION, bearer(rakib)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_asManager_passesCurrentUserToService() throws Exception {
        when(employeeService.getById(eq(7L), any(EmployeeUserDetails.class))).thenReturn(EmployeeResponse.from(rakib));

        mockMvc.perform(get("/api/employees/7").header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void create_asAdmin_returns201() throws Exception {
        when(employeeService.create(any())).thenReturn(EmployeeResponse.from(rakib));

        mockMvc.perform(post("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"New Hire","email":"new@leaveflow.com","password":"Welcome123",
                                 "role":"EMPLOYEE","department":"Engineering","designation":"Engineer",
                                 "managerId":2,"joiningDate":"2026-10-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("rakib@leaveflow.com"));
    }

    @Test
    void create_withInvalidBody_returnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"email\":\"bad\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.role").value("Role is required"))
                .andExpect(jsonPath("$.fieldErrors.joiningDate").value("Joining date is required"));
        verifyNoInteractions(employeeService);
    }

    @Test
    void create_asManager_isForbidden() throws Exception {
        mockMvc.perform(post("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivate_asAdmin_returns204_andActivateReturnsEmployee() throws Exception {
        when(employeeService.activate(7L)).thenReturn(EmployeeResponse.from(rakib));

        mockMvc.perform(delete("/api/employees/7").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/employees/7/activate").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        verify(employeeService).deactivate(eq(7L), any(EmployeeUserDetails.class));
    }

    private String bearer(Employee employee) {
        return "Bearer " + jwtService.generateAccessToken(EmployeeUserDetails.fromEmployee(employee)).token();
    }
}
