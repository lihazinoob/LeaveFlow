package com.misl.leaveflow.dashboard;

import com.misl.leaveflow.dashboard.dto.LabelTotal;
import com.misl.leaveflow.dashboard.dto.MyDashboardResponse;
import com.misl.leaveflow.dashboard.dto.OrganisationDashboardResponse;
import com.misl.leaveflow.dashboard.dto.TeamDashboardResponse;
import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.EmployeeRepository;
import com.misl.leaveflow.employee.Role;
import com.misl.leaveflow.holiday.PublicHoliday;
import com.misl.leaveflow.holiday.PublicHolidayRepository;
import com.misl.leaveflow.leave.LeaveBalanceService;
import com.misl.leaveflow.leave.LeavePermissions;
import com.misl.leaveflow.leave.LeaveRequest;
import com.misl.leaveflow.leave.LeaveRequestService;
import com.misl.leaveflow.leave.LeaveStatus;
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

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

    @Mock
    private LeaveStatsRepository statsRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private PublicHolidayRepository holidayRepository;
    @Mock
    private LeaveBalanceService balanceService;
    @Mock
    private LeaveRequestService leaveRequestService;

    private DashboardService service;

    private final Employee admin = TestEmployees.employee(1L, "admin@leaveflow.com", Role.ADMIN);
    private final Employee manager = TestEmployees.employee(2L, "tanvir@leaveflow.com", Role.MANAGER);
    private final Employee rakib = TestEmployees.employee(7L, "rakib@leaveflow.com", Role.EMPLOYEE);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Dhaka")).toInstant(), ZoneId.of("Asia/Dhaka"));
        service = new DashboardService(statsRepository, employeeRepository, holidayRepository, balanceService,
                leaveRequestService, new LeavePermissions(clock), clock);
        rakib.setManager(manager);
    }

    @Test
    void myDashboard_countsPending_andShowsAtMostThreeHolidays() {
        LeaveRequest pending = TestLeaves.request(1L, rakib, LeaveStatus.PENDING, TODAY.plusDays(3), TODAY.plusDays(4));
        LeaveRequest approved = TestLeaves.request(2L, rakib, LeaveStatus.APPROVED, TODAY.plusDays(9), TODAY.plusDays(9));
        when(statsRepository.findUpcomingForEmployee(eq(7L), eq(TODAY), any())).thenReturn(List.of(pending, approved));
        when(holidayRepository.findByDateBetweenOrderByDateAsc(TODAY, TODAY.plusMonths(6)))
                .thenReturn(IntStream.range(0, 5).mapToObj(i -> holiday(TODAY.plusDays(10L + i))).toList());
        when(balanceService.getBalances(7L, 2026)).thenReturn(List.of());

        MyDashboardResponse dashboard = service.getMyDashboard(EmployeeUserDetails.fromEmployee(rakib));

        assertThat(dashboard.year()).isEqualTo(2026);
        assertThat(dashboard.upcomingLeaves()).hasSize(2);
        assertThat(dashboard.pendingRequests()).isEqualTo(1);
        assertThat(dashboard.upcomingHolidays()).hasSize(3);
        assertThat(dashboard.upcomingLeaves().get(0).canCancel()).isTrue();
    }

    @Test
    void teamDashboard_forManager_isScopedToDirectReports() {
        when(employeeRepository.findByManagerIdAndActiveTrueOrderByFullNameAsc(2L)).thenReturn(List.of(rakib));
        when(statsRepository.findInRangeForTeam(eq(2L), any(), any(), any())).thenReturn(List.of());
        when(leaveRequestService.getPendingApprovals(any())).thenReturn(List.of());

        TeamDashboardResponse dashboard = service.getTeamDashboard(EmployeeUserDetails.fromEmployee(manager));

        assertThat(dashboard.scope()).isEqualTo("TEAM");
        assertThat(dashboard.teamSize()).isEqualTo(1);
        verify(statsRepository).findInRangeForTeam(2L, TODAY, TODAY, Set.of(LeaveStatus.APPROVED));
        verify(statsRepository).findInRangeForTeam(eq(2L), eq(TODAY.plusDays(1)), eq(TODAY.plusDays(14)), any());
        verify(statsRepository, never()).findInRange(any(), any(), any());
    }

    @Test
    void teamDashboard_upcomingExcludesLeaveThatAlreadyStarted() {
        LeaveRequest ongoing = TestLeaves.request(1L, rakib, LeaveStatus.APPROVED, TODAY.minusDays(1), TODAY.plusDays(2));
        LeaveRequest future = TestLeaves.request(2L, rakib, LeaveStatus.PENDING, TODAY.plusDays(5), TODAY.plusDays(6));
        when(employeeRepository.countByActiveTrue()).thenReturn(10L);
        when(statsRepository.findInRange(eq(TODAY.plusDays(1)), eq(TODAY.plusDays(14)), any()))
                .thenReturn(List.of(ongoing, future));
        when(leaveRequestService.getPendingApprovals(any())).thenReturn(List.of());

        TeamDashboardResponse dashboard = service.getTeamDashboard(EmployeeUserDetails.fromEmployee(admin));

        assertThat(dashboard.upcomingAbsences()).extracting("requestId").containsExactly(2L);
    }

    @Test
    void teamDashboard_forAdmin_coversTheWholeOrganisation() {
        LeaveRequest today = TestLeaves.request(1L, rakib, LeaveStatus.APPROVED, TODAY.minusDays(1), TODAY.plusDays(1));
        when(employeeRepository.countByActiveTrue()).thenReturn(10L);
        when(statsRepository.findInRange(TODAY, TODAY, Set.of(LeaveStatus.APPROVED))).thenReturn(List.of(today));
        when(leaveRequestService.getPendingApprovals(any())).thenReturn(List.of());

        TeamDashboardResponse dashboard = service.getTeamDashboard(EmployeeUserDetails.fromEmployee(admin));

        assertThat(dashboard.scope()).isEqualTo("ORGANISATION");
        assertThat(dashboard.teamSize()).isEqualTo(10);
        assertThat(dashboard.onLeaveToday()).singleElement()
                .satisfies(item -> assertThat(item.employeeName()).isEqualTo(rakib.getFullName()));
        verify(statsRepository, never()).findInRangeForTeam(anyLong(), any(), any(), any());
    }

    @Test
    void organisationDashboard_fillsAllTwelveMonths_andSumsApprovedDays() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);
        when(statsRepository.approvedDaysByType(from, to)).thenReturn(List.of(
                new LabelTotal("Annual Leave", "#2563eb", 12L), new LabelTotal("Sick Leave", "#dc2626", 4L)));
        when(statsRepository.approvedDaysByMonth(from, to)).thenReturn(List.of(
                new Object[]{3, 5L}, new Object[]{9, 11L}));
        when(employeeRepository.countByActiveTrue()).thenReturn(10L);
        when(statsRepository.countByStatusAndEmployeeActiveTrue(LeaveStatus.PENDING)).thenReturn(4L);

        OrganisationDashboardResponse dashboard = service.getOrganisationDashboard(null);

        assertThat(dashboard.year()).isEqualTo(2026);
        assertThat(dashboard.approvedDaysThisYear()).isEqualTo(16);
        assertThat(dashboard.pendingRequests()).isEqualTo(4);
        assertThat(dashboard.approvedDaysByMonth()).hasSize(12);
        assertThat(dashboard.approvedDaysByMonth().get(0)).isEqualTo(new LabelTotal("Jan", 0L));
        assertThat(dashboard.approvedDaysByMonth().get(2)).isEqualTo(new LabelTotal("Mar", 5L));
        assertThat(dashboard.approvedDaysByMonth().get(8)).isEqualTo(new LabelTotal("Sep", 11L));
    }

    private static PublicHoliday holiday(LocalDate date) {
        PublicHoliday holiday = new PublicHoliday();
        holiday.setDate(date);
        holiday.setName("Holiday " + date);
        return holiday;
    }
}
