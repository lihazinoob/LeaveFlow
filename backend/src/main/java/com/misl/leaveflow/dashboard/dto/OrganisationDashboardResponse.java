package com.misl.leaveflow.dashboard.dto;

import java.util.List;

/** Admin-only company overview with chart data for the selected year. */
public record OrganisationDashboardResponse(
        int year,
        long activeEmployees,
        long onLeaveToday,
        long pendingRequests,
        long approvedDaysThisYear,
        List<LabelTotal> approvedDaysByType,
        List<LabelTotal> approvedDaysByMonth,
        List<LabelTotal> approvedDaysByDepartment,
        List<LabelTotal> headcountByDepartment
) {
}
