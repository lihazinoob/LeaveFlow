package com.misl.leaveflow.dashboard.dto;

import java.util.List;

/**
 * "Who is away" overview. For a manager the scope is their direct reports;
 * for an admin it is the whole organisation.
 *
 * @param scope             TEAM or ORGANISATION
 * @param upcomingAbsences  approved and pending leave starting within the next 14 days
 * @param pendingApprovals  requests waiting for this user's decision
 */
public record TeamDashboardResponse(
        String scope,
        long teamSize,
        List<AbsenceItem> onLeaveToday,
        List<AbsenceItem> upcomingAbsences,
        long pendingApprovals
) {
}
