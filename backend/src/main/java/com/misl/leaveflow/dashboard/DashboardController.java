package com.misl.leaveflow.dashboard;

import com.misl.leaveflow.dashboard.dto.CalendarResponse;
import com.misl.leaveflow.dashboard.dto.MyDashboardResponse;
import com.misl.leaveflow.dashboard.dto.OrganisationDashboardResponse;
import com.misl.leaveflow.dashboard.dto.TeamDashboardResponse;
import com.misl.leaveflow.security.EmployeeUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.YearMonth;

@Tag(name = "Dashboards & calendar")
@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final CalendarService calendarService;
    private final Clock clock;

    @Operation(summary = "Personal dashboard: balances, upcoming leave, holidays")
    @GetMapping("/api/dashboard/me")
    public MyDashboardResponse myDashboard(@AuthenticationPrincipal EmployeeUserDetails user) {
        return dashboardService.getMyDashboard(user);
    }

    @Operation(summary = "Who is away: direct reports (manager) or everyone (admin)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/api/dashboard/team")
    public TeamDashboardResponse teamDashboard(@AuthenticationPrincipal EmployeeUserDetails user) {
        return dashboardService.getTeamDashboard(user);
    }

    @Operation(summary = "Company overview and chart data (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/dashboard/organisation")
    public OrganisationDashboardResponse organisationDashboard(@RequestParam(required = false) Integer year) {
        return dashboardService.getOrganisationDashboard(year);
    }

    @Operation(summary = "Leave calendar for a month, e.g. ?month=2026-10 (defaults to the current month)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/api/calendar")
    public CalendarResponse calendar(@AuthenticationPrincipal EmployeeUserDetails user,
                                     @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return calendarService.getMonth(user, month != null ? month : YearMonth.now(clock));
    }
}
