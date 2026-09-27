package com.misl.leaveflow.leave;

import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.EmployeeRepository;
import com.misl.leaveflow.employee.Role;
import com.misl.leaveflow.leave.dto.LeaveBalanceResponse;
import com.misl.leaveflow.leave.type.LeaveTypeRepository;
import com.misl.leaveflow.security.EmployeeUserDetails;
import com.misl.leaveflow.support.TestEmployees;
import com.misl.leaveflow.support.TestLeaves;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaveBalanceServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private LeaveTypeRepository leaveTypeRepository;
    @Mock
    private EmployeeRepository employeeRepository;

    private LeaveBalanceService service;

    private final Employee manager = TestEmployees.employee(2L, "tanvir@leaveflow.com", Role.MANAGER);
    private final Employee rakib = TestEmployees.employee(7L, "rakib@leaveflow.com", Role.EMPLOYEE);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-27T00:00:00Z"), ZoneId.of("Asia/Dhaka"));
        service = new LeaveBalanceService(leaveRequestRepository, leaveTypeRepository, employeeRepository, clock);
        rakib.setManager(manager);
        when(leaveTypeRepository.findAllByOrderByIdAsc())
                .thenReturn(List.of(TestLeaves.annualLeave(), TestLeaves.unpaidLeave()));
        when(leaveRequestRepository.sumWorkingDays(eq(7L), eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 12, 31)),
                any(), anyLong()))
                .thenReturn(List.of(
                        new LeaveUsage(1L, LeaveStatus.APPROVED, 5L),
                        new LeaveUsage(1L, LeaveStatus.PENDING, 2L),
                        new LeaveUsage(4L, LeaveStatus.APPROVED, 3L)));
    }

    @Test
    void balances_areQuotaMinusApprovedMinusPending() {
        List<LeaveBalanceResponse> balances = service.getBalances(7L, 2026);

        LeaveBalanceResponse annual = balances.get(0);
        assertThat(annual.leaveTypeCode()).isEqualTo("ANNUAL");
        assertThat(annual.used()).isEqualTo(5);
        assertThat(annual.pending()).isEqualTo(2);
        assertThat(annual.available()).isEqualTo(8);

        LeaveBalanceResponse unpaid = balances.get(1);
        assertThat(unpaid.used()).isEqualTo(3);
        assertThat(unpaid.available()).isNull();
    }

    @Test
    void availableDays_forQuotaType_andNullForUnlimited() {
        assertThat(service.getAvailableDays(7L, TestLeaves.annualLeave(), 2026, -1L)).isEqualTo(8);
        assertThat(service.getAvailableDays(7L, TestLeaves.unpaidLeave(), 2026, -1L)).isNull();
    }

    @Test
    void balancesFor_defaultsToOwnBalanceAndCurrentYear() {
        assertThat(service.getBalancesFor(EmployeeUserDetails.fromEmployee(rakib), null, null)).hasSize(2);
    }

    @Test
    void balancesFor_managerMayViewDirectReport_butColleagueMayNot() {
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(rakib));
        Employee colleague = TestEmployees.employee(8L, "sadia@leaveflow.com", Role.EMPLOYEE);

        assertThat(service.getBalancesFor(EmployeeUserDetails.fromEmployee(manager), 7L, 2026)).hasSize(2);
        assertThatThrownBy(() -> service.getBalancesFor(EmployeeUserDetails.fromEmployee(colleague), 7L, 2026))
                .isInstanceOf(AccessDeniedException.class);
    }
}
