package com.misl.leaveflow.leave;

import com.misl.leaveflow.common.dto.PageResponse;
import com.misl.leaveflow.common.exception.BusinessRuleException;
import com.misl.leaveflow.common.exception.ResourceNotFoundException;
import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.EmployeeRepository;
import com.misl.leaveflow.employee.Role;
import com.misl.leaveflow.leave.dto.LeaveApplyRequest;
import com.misl.leaveflow.leave.dto.LeaveHistoryResponse;
import com.misl.leaveflow.leave.dto.LeavePreviewResponse;
import com.misl.leaveflow.leave.dto.LeaveRequestResponse;
import com.misl.leaveflow.leave.type.LeaveType;
import com.misl.leaveflow.leave.type.LeaveTypeRepository;
import com.misl.leaveflow.security.EmployeeUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Applying for leave, editing a pending request, and the read-only views (mine, team, approvals, all).
 * Status changes (approve / reject / cancel) live in {@link LeaveWorkflowService}.
 * <p>
 * A request must pass every rule in {@link #evaluate}. The same method powers the live preview,
 * so the form shows exactly the errors that submitting would produce.
 */
@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveRequestHistoryRepository historyRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final WorkingDayCalculator workingDayCalculator;
    private final LeaveBalanceService balanceService;
    private final LeavePermissions permissions;
    private final Clock clock;

    @Transactional(readOnly = true)
    public LeavePreviewResponse preview(EmployeeUserDetails user, Long leaveTypeId, LocalDate start, LocalDate end,
                                        Long excludeRequestId) {
        long exclude = excludeRequestId != null ? excludeRequestId : LeaveBalanceService.NO_EXCLUSION;
        return evaluate(user.getId(), findLeaveType(leaveTypeId), start, end, exclude);
    }

    @Transactional
    public LeaveRequestResponse apply(EmployeeUserDetails user, LeaveApplyRequest request) {
        Employee employee = employeeRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee", user.getId()));
        LeaveType type = findLeaveType(request.leaveTypeId());
        LeavePreviewResponse check = evaluate(employee.getId(), type, request.startDate(), request.endDate(),
                LeaveBalanceService.NO_EXCLUSION);
        rejectIfInvalid(check);

        LeaveRequest leave = new LeaveRequest();
        leave.setEmployee(employee);
        leave.setLeaveType(type);
        leave.setStartDate(request.startDate());
        leave.setEndDate(request.endDate());
        leave.setWorkingDays(check.workingDays());
        leave.setReason(request.reason().trim());
        leave.setStatus(LeaveStatus.PENDING);
        leaveRequestRepository.save(leave);

        historyRepository.save(new LeaveRequestHistory(leave, null, LeaveStatus.PENDING, employee, "Request submitted"));
        return permissions.toResponse(leave, user);
    }

    /** Only the owner can edit, and only while the request is still pending. */
    @Transactional
    public LeaveRequestResponse update(EmployeeUserDetails user, Long id, LeaveApplyRequest request) {
        LeaveRequest leave = findWithDetails(id);
        if (!permissions.isOwner(user, leave)) {
            throw new AccessDeniedException("Only the employee who applied can edit this request");
        }
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new BusinessRuleException("Only pending requests can be edited");
        }

        LeaveType type = findLeaveType(request.leaveTypeId());
        LeavePreviewResponse check = evaluate(user.getId(), type, request.startDate(), request.endDate(), id);
        rejectIfInvalid(check);

        leave.setLeaveType(type);
        leave.setStartDate(request.startDate());
        leave.setEndDate(request.endDate());
        leave.setWorkingDays(check.workingDays());
        leave.setReason(request.reason().trim());
        leaveRequestRepository.saveAndFlush(leave);

        historyRepository.save(new LeaveRequestHistory(leave, LeaveStatus.PENDING, LeaveStatus.PENDING,
                leave.getEmployee(), "Request edited"));
        return permissions.toResponse(leave, user);
    }

    @Transactional(readOnly = true)
    public LeaveRequestResponse getById(EmployeeUserDetails user, Long id) {
        LeaveRequest leave = findWithDetails(id);
        if (!permissions.canView(user, leave)) {
            throw new AccessDeniedException("You cannot view this leave request");
        }
        return permissions.toResponse(leave, user);
    }

    @Transactional(readOnly = true)
    public List<LeaveHistoryResponse> getHistory(EmployeeUserDetails user, Long id) {
        LeaveRequest leave = findWithDetails(id);
        if (!permissions.canView(user, leave)) {
            throw new AccessDeniedException("You cannot view this leave request");
        }
        return historyRepository.findByLeaveRequestIdOrderByChangedAtAscIdAsc(id).stream()
                .map(LeaveHistoryResponse::from)
                .toList();
    }

    /** The logged-in user's own requests, newest first. */
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> getMyRequests(EmployeeUserDetails user, LeaveStatus status, Integer year) {
        List<Specification<LeaveRequest>> filters = new ArrayList<>();
        filters.add(LeaveSpecifications.ofEmployee(user.getId()));
        addOptionalFilters(filters, status, year);
        return findAndMap(filters, Sort.by(Sort.Direction.DESC, "startDate"), user);
    }

    /** Requests of the logged-in manager's direct reports. */
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> getTeamRequests(EmployeeUserDetails user, LeaveStatus status, Integer year) {
        List<Specification<LeaveRequest>> filters = new ArrayList<>();
        filters.add(LeaveSpecifications.ofTeamManagedBy(user.getId()));
        addOptionalFilters(filters, status, year);
        return findAndMap(filters, Sort.by(Sort.Direction.DESC, "startDate"), user);
    }

    /**
     * The approval inbox: pending requests this user may decide on, earliest leave first.
     * Admins see everyone's except their own; managers see their direct reports'.
     */
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> getPendingApprovals(EmployeeUserDetails user) {
        List<Specification<LeaveRequest>> filters = new ArrayList<>();
        filters.add(LeaveSpecifications.hasStatus(LeaveStatus.PENDING));
        filters.add(LeaveSpecifications.ofActiveEmployees());
        filters.add(user.getRole() == Role.ADMIN
                ? LeaveSpecifications.notOfEmployee(user.getId())
                : LeaveSpecifications.ofTeamManagedBy(user.getId()));
        return findAndMap(filters, Sort.by(Sort.Direction.ASC, "startDate"), user);
    }

    /** Admin view of every request, with optional filters and paging. */
    @Transactional(readOnly = true)
    public PageResponse<LeaveRequestResponse> search(EmployeeUserDetails user, LeaveStatus status, Long leaveTypeId,
                                                     String employeeSearch, LocalDate from, LocalDate to,
                                                     Pageable pageable) {
        List<Specification<LeaveRequest>> filters = new ArrayList<>();
        if (status != null) {
            filters.add(LeaveSpecifications.hasStatus(status));
        }
        if (leaveTypeId != null) {
            filters.add(LeaveSpecifications.hasLeaveType(leaveTypeId));
        }
        if (StringUtils.hasText(employeeSearch)) {
            filters.add(LeaveSpecifications.employeeMatches(employeeSearch));
        }
        if (from != null) {
            filters.add(LeaveSpecifications.endsOnOrAfter(from));
        }
        if (to != null) {
            filters.add(LeaveSpecifications.startsOnOrBefore(to));
        }
        return PageResponse.from(leaveRequestRepository.findAll(Specification.allOf(filters), pageable)
                .map(leave -> permissions.toResponse(leave, user)));
    }

    /**
     * Applies every business rule and collects all problems instead of stopping at the first,
     * so the user can fix everything at once.
     */
    LeavePreviewResponse evaluate(Long employeeId, LeaveType type, LocalDate start, LocalDate end, long excludeId) {
        List<String> problems = new ArrayList<>();

        if (end.isBefore(start)) {
            problems.add("End date must be on or after the start date");
            return LeavePreviewResponse.of(0, null, problems);
        }
        if (start.getYear() != end.getYear()) {
            problems.add("A leave request cannot span two calendar years. Please split it into two requests.");
            return LeavePreviewResponse.of(0, null, problems);
        }
        if (start.isBefore(LocalDate.now(clock))) {
            problems.add("Leave cannot start in the past");
        }

        int workingDays = workingDayCalculator.countWorkingDays(start, end);
        if (workingDays == 0) {
            problems.add("The selected dates contain no working days (only weekends or public holidays)");
        }
        if (leaveRequestRepository.existsOverlapping(employeeId, start, end, LeaveStatus.activeStatuses(), excludeId)) {
            problems.add("You already have a pending or approved leave that overlaps these dates");
        }

        Integer available = balanceService.getAvailableDays(employeeId, type, start.getYear(), excludeId);
        if (available != null && workingDays > available) {
            problems.add("Not enough " + type.getName() + " balance: " + workingDays + " day(s) requested, "
                    + Math.max(available, 0) + " available");
        }
        return LeavePreviewResponse.of(workingDays, available, problems);
    }

    private static void rejectIfInvalid(LeavePreviewResponse check) {
        if (!check.valid()) {
            throw new BusinessRuleException(String.join(". ", check.problems()));
        }
    }

    private void addOptionalFilters(List<Specification<LeaveRequest>> filters, LeaveStatus status, Integer year) {
        if (status != null) {
            filters.add(LeaveSpecifications.hasStatus(status));
        }
        if (year != null) {
            filters.add(LeaveSpecifications.overlaps(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)));
        }
    }

    private List<LeaveRequestResponse> findAndMap(List<Specification<LeaveRequest>> filters, Sort sort,
                                                  EmployeeUserDetails user) {
        return leaveRequestRepository.findAll(Specification.allOf(filters), sort).stream()
                .map(leave -> permissions.toResponse(leave, user))
                .toList();
    }

    private LeaveType findLeaveType(Long id) {
        return leaveTypeRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Unknown leave type: " + id));
    }

    private LeaveRequest findWithDetails(Long id) {
        return leaveRequestRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request", id));
    }
}
