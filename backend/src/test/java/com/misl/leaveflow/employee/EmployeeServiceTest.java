package com.misl.leaveflow.employee;

import com.misl.leaveflow.auth.refresh.RefreshTokenService;
import com.misl.leaveflow.auth.refresh.RevokedReason;
import com.misl.leaveflow.common.exception.BusinessRuleException;
import com.misl.leaveflow.common.exception.ConflictException;
import com.misl.leaveflow.common.exception.ResourceNotFoundException;
import com.misl.leaveflow.employee.dto.EmployeeCreateRequest;
import com.misl.leaveflow.employee.dto.EmployeeResponse;
import com.misl.leaveflow.employee.dto.EmployeeUpdateRequest;
import com.misl.leaveflow.leave.LeaveWorkflowService;
import com.misl.leaveflow.security.EmployeeUserDetails;
import com.misl.leaveflow.support.TestEmployees;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private LeaveWorkflowService leaveWorkflowService;

    @InjectMocks
    private EmployeeService employeeService;

    private final Employee admin = TestEmployees.employee(1L, "admin@leaveflow.com", Role.ADMIN);
    private final Employee manager = TestEmployees.employee(2L, "tanvir@leaveflow.com", Role.MANAGER);
    private final Employee rakib = TestEmployees.employee(7L, "rakib@leaveflow.com", Role.EMPLOYEE);
    private final EmployeeUserDetails adminUser = EmployeeUserDetails.fromEmployee(admin);

    {
        manager.setManager(admin);
        rakib.setManager(manager);
    }

    // ---------- create ----------

    @Test
    void create_normalisesInput_hashesPassword_andAssignsManager() {
        when(employeeRepository.existsByEmail("new.hire@leaveflow.com")).thenReturn(false);
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(passwordEncoder.encode("Welcome123")).thenReturn("$2a$10$hashed");
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeResponse response = employeeService.create(createRequest(" New.Hire@LeaveFlow.com ", Role.EMPLOYEE, 2L));

        ArgumentCaptor<Employee> saved = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("new.hire@leaveflow.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$10$hashed");
        assertThat(saved.getValue().getFullName()).isEqualTo("New Hire");
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(response.managerName()).isEqualTo(manager.getFullName());
    }

    @Test
    void create_withDuplicateEmail_isConflict() {
        when(employeeRepository.existsByEmail("rakib@leaveflow.com")).thenReturn(true);

        assertThatThrownBy(() -> employeeService.create(createRequest("rakib@leaveflow.com", Role.EMPLOYEE, 2L)))
                .isInstanceOf(ConflictException.class);
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void create_employeeWithoutManager_isRejected() {
        assertThatThrownBy(() -> employeeService.create(createRequest("x@leaveflow.com", Role.EMPLOYEE, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A manager is required for employees and managers");
    }

    @Test
    void create_adminWithoutManager_isAllowed() {
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeResponse response = employeeService.create(createRequest("boss@leaveflow.com", Role.ADMIN, null));

        assertThat(response.managerId()).isNull();
    }

    @Test
    void create_withManagerWhoIsAPlainEmployee_isRejected() {
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(rakib));

        assertThatThrownBy(() -> employeeService.create(createRequest("x@leaveflow.com", Role.EMPLOYEE, 7L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The selected manager must have the MANAGER or ADMIN role");
    }

    @Test
    void create_withDeactivatedManager_isRejected() {
        manager.setActive(false);
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(manager));

        assertThatThrownBy(() -> employeeService.create(createRequest("x@leaveflow.com", Role.EMPLOYEE, 2L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The selected manager is deactivated");
    }

    @Test
    void create_withUnknownManager_isRejected() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.create(createRequest("x@leaveflow.com", Role.EMPLOYEE, 99L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The selected manager does not exist");
    }

    // ---------- update ----------

    @Test
    void update_changesFields() {
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(rakib));
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(manager));

        EmployeeResponse response = employeeService.update(7L,
                updateRequest("Rakib@LeaveFlow.com", Role.EMPLOYEE, 2L, "Lead Engineer"), adminUser);

        assertThat(response.designation()).isEqualTo("Lead Engineer");
        assertThat(rakib.getDesignation()).isEqualTo("Lead Engineer");
    }

    @Test
    void update_toEmailOfAnotherEmployee_isConflict() {
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(rakib));
        when(employeeRepository.existsByEmailAndIdNot("tanvir@leaveflow.com", 7L)).thenReturn(true);

        assertThatThrownBy(() -> employeeService.update(7L,
                updateRequest("tanvir@leaveflow.com", Role.EMPLOYEE, 2L, "Engineer"), adminUser))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_selfAsManager_isRejected() {
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(manager));

        assertThatThrownBy(() -> employeeService.update(2L,
                updateRequest("tanvir@leaveflow.com", Role.MANAGER, 2L, "Manager"), adminUser))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("An employee cannot be their own manager");
    }

    @Test
    void update_creatingReportingLoop_isRejected() {
        // Tanvir manages Sadia (a MANAGER); making Sadia Tanvir's manager would create a loop.
        Employee sadia = TestEmployees.employee(5L, "sadia@leaveflow.com", Role.MANAGER);
        sadia.setManager(manager);
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(sadia));

        assertThatThrownBy(() -> employeeService.update(2L,
                updateRequest("tanvir@leaveflow.com", Role.MANAGER, 5L, "Manager"), adminUser))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("loop");
    }

    @Test
    void update_demotingManagerWithActiveTeam_isRejected() {
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(employeeRepository.existsByManagerIdAndActiveTrue(2L)).thenReturn(true);

        assertThatThrownBy(() -> employeeService.update(2L,
                updateRequest("tanvir@leaveflow.com", Role.EMPLOYEE, 1L, "Engineer"), adminUser))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Reassign their team");
    }

    @Test
    void update_ownRole_isRejected() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> employeeService.update(1L,
                updateRequest("admin@leaveflow.com", Role.MANAGER, null, "HR"), adminUser))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("You cannot change your own role");
    }

    // ---------- deactivate / activate ----------

    @Test
    void deactivate_setsInactive_signsTheEmployeeOut_andCancelsPendingLeave() {
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(rakib));

        employeeService.deactivate(7L, adminUser);

        assertThat(rakib.isActive()).isFalse();
        verify(refreshTokenService).revokeAllForEmployee(7L, RevokedReason.ACCOUNT_DEACTIVATED);
        verify(leaveWorkflowService).cancelPendingRequestsOf(7L, 1L);
    }

    @Test
    void deactivate_self_isRejected() {
        assertThatThrownBy(() -> employeeService.deactivate(1L, adminUser))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("You cannot deactivate your own account");
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void deactivate_managerWithActiveTeam_isRejected() {
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(employeeRepository.existsByManagerIdAndActiveTrue(2L)).thenReturn(true);

        assertThatThrownBy(() -> employeeService.deactivate(2L, adminUser))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(manager.isActive()).isTrue();
        verifyNoInteractions(refreshTokenService, leaveWorkflowService);
    }

    @Test
    void activate_setsActive() {
        rakib.setActive(false);
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(rakib));

        assertThat(employeeService.activate(7L).active()).isTrue();
    }

    // ---------- read access ----------

    @Test
    void getById_managerCanViewDirectReport_butNotOthers() {
        Employee stranger = TestEmployees.employee(9L, "arif@leaveflow.com", Role.EMPLOYEE);
        stranger.setManager(admin);
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(rakib));
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(stranger));
        EmployeeUserDetails managerUser = EmployeeUserDetails.fromEmployee(manager);

        assertThat(employeeService.getById(7L, managerUser).email()).isEqualTo("rakib@leaveflow.com");
        assertThatThrownBy(() -> employeeService.getById(9L, managerUser))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getById_unknownEmployee_isNotFound() {
        when(employeeRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.getById(404L, adminUser))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- helpers ----------

    private static EmployeeCreateRequest createRequest(String email, Role role, Long managerId) {
        return new EmployeeCreateRequest(" New Hire ", email, "Welcome123", role, "Engineering", "Engineer",
                managerId, LocalDate.of(2026, 10, 1));
    }

    private static EmployeeUpdateRequest updateRequest(String email, Role role, Long managerId, String designation) {
        return new EmployeeUpdateRequest("Updated Name", email, role, "Engineering", designation, managerId,
                LocalDate.of(2024, 1, 1));
    }
}
