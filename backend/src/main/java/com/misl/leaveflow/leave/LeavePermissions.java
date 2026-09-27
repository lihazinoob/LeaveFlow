package com.misl.leaveflow.leave;

import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.Role;
import com.misl.leaveflow.leave.dto.LeaveRequestResponse;
import com.misl.leaveflow.security.EmployeeUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Who may do what with a leave request. Kept in one place so the checks performed on an action
 * and the "can..." flags sent to the frontend can never disagree.
 * <ul>
 *   <li>Owner: the employee who applied.</li>
 *   <li>Reviewer: the owner's direct manager, or any admin, but never the owner themselves.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class LeavePermissions {

    private final Clock clock;

    public boolean isOwner(EmployeeUserDetails user, LeaveRequest request) {
        return request.getEmployee().getId().equals(user.getId());
    }

    public boolean isReviewer(EmployeeUserDetails user, LeaveRequest request) {
        return !isOwner(user, request) && (user.getRole() == Role.ADMIN || isManagerOfOwner(user, request));
    }

    public boolean canView(EmployeeUserDetails user, LeaveRequest request) {
        return isOwner(user, request) || user.getRole() == Role.ADMIN || isManagerOfOwner(user, request);
    }

    public boolean canEdit(EmployeeUserDetails user, LeaveRequest request) {
        return isOwner(user, request) && request.getStatus() == LeaveStatus.PENDING;
    }

    /** Pending requests can always be withdrawn; approved ones only before the leave starts. */
    public boolean canCancel(EmployeeUserDetails user, LeaveRequest request) {
        boolean allowedPerson = isOwner(user, request) || user.getRole() == Role.ADMIN;
        return allowedPerson && (request.getStatus() == LeaveStatus.PENDING || isApprovedAndNotStarted(request));
    }

    public boolean canReview(EmployeeUserDetails user, LeaveRequest request) {
        return request.getStatus() == LeaveStatus.PENDING && isReviewer(user, request);
    }

    public boolean isApprovedAndNotStarted(LeaveRequest request) {
        return request.getStatus() == LeaveStatus.APPROVED && request.getStartDate().isAfter(LocalDate.now(clock));
    }

    public LeaveRequestResponse toResponse(LeaveRequest request, EmployeeUserDetails user) {
        return LeaveRequestResponse.from(request, canEdit(user, request), canCancel(user, request),
                canReview(user, request));
    }

    private boolean isManagerOfOwner(EmployeeUserDetails user, LeaveRequest request) {
        Employee manager = request.getEmployee().getManager();
        return manager != null && manager.getId().equals(user.getId());
    }
}
