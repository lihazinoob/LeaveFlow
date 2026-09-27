package com.misl.leaveflow.leave;

/** One row of the balance query: total working days of one leave type in one status. */
public record LeaveUsage(Long leaveTypeId, LeaveStatus status, Long totalDays) {
}
