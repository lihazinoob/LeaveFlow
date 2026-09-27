package com.misl.leaveflow.dashboard;

import com.misl.leaveflow.dashboard.dto.AbsenceItem;
import com.misl.leaveflow.dashboard.dto.LabelTotal;
import com.misl.leaveflow.dashboard.dto.MyDashboardResponse;
import com.misl.leaveflow.dashboard.dto.OrganisationDashboardResponse;
import com.misl.leaveflow.dashboard.dto.TeamDashboardResponse;
import com.misl.leaveflow.employee.EmployeeRepository;
import com.misl.leaveflow.employee.Role;
import com.misl.leaveflow.holiday.PublicHolidayRepository;
import com.misl.leaveflow.holiday.dto.HolidayResponse;
import com.misl.leaveflow.leave.LeaveBalanceService;
import com.misl.leaveflow.leave.LeavePermissions;
import com.misl.leaveflow.leave.LeaveRequest;
import com.misl.leaveflow.leave.LeaveRequestService;
import com.misl.leaveflow.leave.LeaveStatus;
import com.misl.leaveflow.leave.dto.LeaveRequestResponse;
import com.misl.leaveflow.security.EmployeeUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the three dashboards. Each is a separate endpoint so that each role only calls,
 * and is only allowed to call, what it needs:
 * <ul>
 *   <li>my: every user (balances, upcoming leave, holidays)</li>
 *   <li>team: managers (their direct reports) and admins (everyone)</li>
 *   <li>organisation: admins only (headcount and chart data)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int UPCOMING_DAYS = 14;
    private static final int MAX_UPCOMING_HOLIDAYS = 3;
    private static final Set<LeaveStatus> PENDING_OR_APPROVED = Set.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);

    private final LeaveStatsRepository statsRepository;
    private final EmployeeRepository employeeRepository;
    private final PublicHolidayRepository holidayRepository;
    private final LeaveBalanceService balanceService;
    private final LeaveRequestService leaveRequestService;
    private final LeavePermissions permissions;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MyDashboardResponse getMyDashboard(EmployeeUserDetails user) {
        LocalDate today = LocalDate.now(clock);

        List<LeaveRequestResponse> upcoming = statsRepository
                .findUpcomingForEmployee(user.getId(), today, PENDING_OR_APPROVED).stream()
                .map(leave -> permissions.toResponse(leave, user))
                .toList();
        long pending = upcoming.stream().filter(leave -> leave.status() == LeaveStatus.PENDING).count();

        List<HolidayResponse> holidays = holidayRepository.findByDateBetweenOrderByDateAsc(today, today.plusMonths(6))
                .stream()
                .limit(MAX_UPCOMING_HOLIDAYS)
                .map(HolidayResponse::from)
                .toList();

        return new MyDashboardResponse(today.getYear(), balanceService.getBalances(user.getId(), today.getYear()),
                upcoming, pending, holidays);
    }

    @Transactional(readOnly = true)
    public TeamDashboardResponse getTeamDashboard(EmployeeUserDetails user) {
        LocalDate today = LocalDate.now(clock);
        boolean admin = user.getRole() == Role.ADMIN;

        long teamSize = admin
                ? employeeRepository.countByActiveTrue()
                : employeeRepository.findByManagerIdAndActiveTrueOrderByFullNameAsc(user.getId()).size();

        List<AbsenceItem> onLeaveToday = absences(user, today, today, Set.of(LeaveStatus.APPROVED));
        // Only leave that starts after today; people already away are listed in onLeaveToday.
        List<AbsenceItem> upcoming = absences(user, today.plusDays(1), today.plusDays(UPCOMING_DAYS), PENDING_OR_APPROVED)
                .stream()
                .filter(item -> item.startDate().isAfter(today))
                .toList();
        long pendingApprovals = leaveRequestService.getPendingApprovals(user).size();

        return new TeamDashboardResponse(admin ? "ORGANISATION" : "TEAM", teamSize, onLeaveToday, upcoming,
                pendingApprovals);
    }

    @Transactional(readOnly = true)
    public OrganisationDashboardResponse getOrganisationDashboard(Integer requestedYear) {
        LocalDate today = LocalDate.now(clock);
        int year = requestedYear != null ? requestedYear : today.getYear();
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);

        List<LabelTotal> byType = statsRepository.approvedDaysByType(from, to);
        long approvedDays = byType.stream().mapToLong(LabelTotal::total).sum();
        long onLeaveToday = statsRepository.findInRange(today, today, Set.of(LeaveStatus.APPROVED)).size();

        return new OrganisationDashboardResponse(
                year,
                employeeRepository.countByActiveTrue(),
                onLeaveToday,
                statsRepository.countByStatusAndEmployeeActiveTrue(LeaveStatus.PENDING),
                approvedDays,
                byType,
                approvedDaysByMonth(from, to),
                statsRepository.approvedDaysByDepartment(from, to),
                statsRepository.activeHeadcountByDepartment());
    }

    /** Always returns 12 entries (Jan..Dec), with 0 for months without leave, so the chart has no gaps. */
    private List<LabelTotal> approvedDaysByMonth(LocalDate from, LocalDate to) {
        Map<Integer, Long> totals = new HashMap<>();
        for (Object[] row : statsRepository.approvedDaysByMonth(from, to)) {
            totals.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        List<LabelTotal> months = new ArrayList<>();
        for (Month month : Month.values()) {
            String label = month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            months.add(new LabelTotal(label, totals.getOrDefault(month.getValue(), 0L)));
        }
        return months;
    }

    private List<AbsenceItem> absences(EmployeeUserDetails user, LocalDate from, LocalDate to,
                                       Set<LeaveStatus> statuses) {
        List<LeaveRequest> leaves = user.getRole() == Role.ADMIN
                ? statsRepository.findInRange(from, to, statuses)
                : statsRepository.findInRangeForTeam(user.getId(), from, to, statuses);
        return leaves.stream().map(AbsenceItem::from).toList();
    }
}
