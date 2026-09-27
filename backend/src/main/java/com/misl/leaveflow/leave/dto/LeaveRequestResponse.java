package com.misl.leaveflow.leave.dto;

import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.leave.LeaveRequest;
import com.misl.leaveflow.leave.LeaveStatus;
import com.misl.leaveflow.leave.type.LeaveType;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A leave request as shown to one particular user. The {@code can...} flags tell the frontend
 * which buttons to show; the backend still re-checks every action when it is performed.
 */
public record LeaveRequestResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeName,
        String department,
        Long leaveTypeId,
        String leaveTypeCode,
        String leaveTypeName,
        String leaveTypeColor,
        LocalDate startDate,
        LocalDate endDate,
        int workingDays,
        String reason,
        LeaveStatus status,
        String reviewedByName,
        String reviewComment,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt,
        boolean canEdit,
        boolean canCancel,
        boolean canReview
) {

    public static LeaveRequestResponse from(LeaveRequest request, boolean canEdit, boolean canCancel,
                                            boolean canReview) {
        Employee employee = request.getEmployee();
        LeaveType type = request.getLeaveType();
        Employee reviewer = request.getReviewedBy();
        return new LeaveRequestResponse(
                request.getId(),
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                employee.getDepartment(),
                type.getId(),
                type.getCode(),
                type.getName(),
                type.getColor(),
                request.getStartDate(),
                request.getEndDate(),
                request.getWorkingDays(),
                request.getReason(),
                request.getStatus(),
                reviewer != null ? reviewer.getFullName() : null,
                request.getReviewComment(),
                request.getReviewedAt(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                canEdit,
                canCancel,
                canReview
        );
    }
}
