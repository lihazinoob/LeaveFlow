package com.misl.leaveflow.leave;

import com.misl.leaveflow.common.exception.BusinessRuleException;
import com.misl.leaveflow.common.exception.ResourceNotFoundException;
import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.EmployeeRepository;
import com.misl.leaveflow.employee.Role;
import com.misl.leaveflow.leave.dto.LeaveRequestResponse;
import com.misl.leaveflow.security.EmployeeUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;

/**
 * Status changes of a leave request: approve, reject, cancel.
 * Every change goes through {@link #changeStatus}, which
 * <ol>
 *   <li>checks the transition is allowed by the {@link LeaveStatus} state machine, and</li>
 *   <li>writes an audit row in the same transaction, so the history can never miss a change.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class LeaveWorkflowService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveRequestHistoryRepository historyRepository;
    private final EmployeeRepository employeeRepository;
    private final LeavePermissions permissions;

    @Transactional
    public LeaveRequestResponse approve(EmployeeUserDetails user, Long id, String comment) {
        LeaveRequest leave = findWithDetails(id);
        requireReviewer(user, leave);
        changeStatus(leave, LeaveStatus.APPROVED, user, comment);
        recordReview(leave, user, comment);
        return save(leave, user);
    }

    @Transactional
    public LeaveRequestResponse reject(EmployeeUserDetails user, Long id, String comment) {
        if (!StringUtils.hasText(comment)) {
            throw new BusinessRuleException("A comment is required when rejecting a request");
        }
        LeaveRequest leave = findWithDetails(id);
        requireReviewer(user, leave);
        changeStatus(leave, LeaveStatus.REJECTED, user, comment);
        recordReview(leave, user, comment);
        return save(leave, user);
    }

    /**
     * The owner (or an admin) withdraws a request. Pending requests can always be cancelled;
     * approved ones only before the first day of leave. The days return to the balance automatically.
     */
    @Transactional
    public LeaveRequestResponse cancel(EmployeeUserDetails user, Long id, String comment) {
        LeaveRequest leave = findWithDetails(id);
        if (!permissions.isOwner(user, leave) && user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only the employee who applied or an admin can cancel this request");
        }
        if (leave.getStatus() == LeaveStatus.APPROVED && !permissions.isApprovedAndNotStarted(leave)) {
            throw new BusinessRuleException("An approved leave can only be cancelled before it starts");
        }
        changeStatus(leave, LeaveStatus.CANCELLED, user, comment);
        return save(leave, user);
    }

    /** Called when an employee is deactivated: their pending requests no longer need a decision. */
    @Transactional
    public int cancelPendingRequestsOf(Long employeeId, Long actorId) {
        Employee actor = employeeRepository.getReferenceById(actorId);
        int cancelled = 0;
        for (LeaveRequest leave : leaveRequestRepository.findByEmployeeIdAndStatus(employeeId, LeaveStatus.PENDING)) {
            leave.setStatus(LeaveStatus.CANCELLED);
            historyRepository.save(new LeaveRequestHistory(leave, LeaveStatus.PENDING, LeaveStatus.CANCELLED, actor,
                    "Cancelled automatically because the employee was deactivated"));
            cancelled++;
        }
        return cancelled;
    }

    private void changeStatus(LeaveRequest leave, LeaveStatus target, EmployeeUserDetails user, String comment) {
        LeaveStatus current = leave.getStatus();
        if (!current.canTransitionTo(target)) {
            throw new BusinessRuleException("A request that is " + lower(current) + " cannot be " + lower(target));
        }
        leave.setStatus(target);
        historyRepository.save(new LeaveRequestHistory(leave, current, target,
                employeeRepository.getReferenceById(user.getId()), trimToNull(comment)));
    }

    private void recordReview(LeaveRequest leave, EmployeeUserDetails user, String comment) {
        leave.setReviewedBy(employeeRepository.getReferenceById(user.getId()));
        leave.setReviewComment(trimToNull(comment));
        leave.setReviewedAt(Instant.now());
    }

    private void requireReviewer(EmployeeUserDetails user, LeaveRequest leave) {
        if (!permissions.isReviewer(user, leave)) {
            throw new AccessDeniedException("Only the employee's manager or an admin can review this request");
        }
    }

    /** Flushing here makes the @Version check (and updated timestamps) happen before the response is built. */
    private LeaveRequestResponse save(LeaveRequest leave, EmployeeUserDetails user) {
        leaveRequestRepository.saveAndFlush(leave);
        return permissions.toResponse(leave, user);
    }

    private LeaveRequest findWithDetails(Long id) {
        return leaveRequestRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request", id));
    }

    private static String lower(LeaveStatus status) {
        return status.name().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }
}
