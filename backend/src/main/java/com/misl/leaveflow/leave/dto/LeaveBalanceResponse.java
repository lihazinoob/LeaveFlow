package com.misl.leaveflow.leave.dto;

/**
 * Balance of one leave type for one employee and year.
 *
 * @param annualQuota yearly allowance; null = unlimited
 * @param used        working days of APPROVED requests
 * @param pending     working days of PENDING requests (reserved, not yet approved)
 * @param available   quota - used - pending; null = unlimited
 */
public record LeaveBalanceResponse(
        Long leaveTypeId,
        String leaveTypeCode,
        String leaveTypeName,
        String color,
        Integer annualQuota,
        int used,
        int pending,
        Integer available
) {
}
