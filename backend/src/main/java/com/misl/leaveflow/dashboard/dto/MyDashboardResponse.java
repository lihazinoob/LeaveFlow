package com.misl.leaveflow.dashboard.dto;

import com.misl.leaveflow.holiday.dto.HolidayResponse;
import com.misl.leaveflow.leave.dto.LeaveBalanceResponse;
import com.misl.leaveflow.leave.dto.LeaveRequestResponse;

import java.util.List;

/** Personal dashboard, shown to every role. */
public record MyDashboardResponse(
        int year,
        List<LeaveBalanceResponse> balances,
        List<LeaveRequestResponse> upcomingLeaves,
        long pendingRequests,
        List<HolidayResponse> upcomingHolidays
) {
}
