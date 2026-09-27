package com.misl.leaveflow.dashboard.dto;

import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.leave.LeaveRequest;
import com.misl.leaveflow.leave.LeaveStatus;

import java.time.LocalDate;

/** A compact "who is away, when" item for dashboards and the calendar. */
public record AbsenceItem(
        Long requestId,
        Long employeeId,
        String employeeName,
        String department,
        String leaveTypeCode,
        String leaveTypeName,
        String color,
        LocalDate startDate,
        LocalDate endDate,
        int workingDays,
        LeaveStatus status
) {

    public static AbsenceItem from(LeaveRequest request) {
        Employee employee = request.getEmployee();
        return new AbsenceItem(request.getId(), employee.getId(), employee.getFullName(), employee.getDepartment(),
                request.getLeaveType().getCode(), request.getLeaveType().getName(), request.getLeaveType().getColor(),
                request.getStartDate(), request.getEndDate(), request.getWorkingDays(), request.getStatus());
    }
}
