package com.misl.leaveflow.leave;

import com.misl.leaveflow.common.exception.ResourceNotFoundException;
import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.EmployeeRepository;
import com.misl.leaveflow.employee.Role;
import com.misl.leaveflow.leave.dto.LeaveBalanceResponse;
import com.misl.leaveflow.leave.type.LeaveType;
import com.misl.leaveflow.leave.type.LeaveTypeRepository;
import com.misl.leaveflow.security.EmployeeUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Leave balances are <b>calculated, not stored</b>:
 * available = yearly quota - approved days - pending days (for leaves starting in that year).
 * There is no balance column that could drift out of sync. Cancelling or rejecting a request
 * "returns" its days automatically, because the request no longer matches the query.
 */
@Service
@RequiredArgsConstructor
public class LeaveBalanceService {

    /** Used when nothing should be excluded; database ids are always positive. */
    static final long NO_EXCLUSION = -1L;

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final Clock clock;

    /**
     * Balances as requested over the API.
     *
     * @param employeeId null = the caller's own balance; anyone else's needs ADMIN or being their direct manager
     * @param year       null = the current year
     */
    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> getBalancesFor(EmployeeUserDetails user, Long employeeId, Integer year) {
        Long targetId = employeeId != null ? employeeId : user.getId();
        if (!targetId.equals(user.getId()) && user.getRole() != Role.ADMIN) {
            Employee target = employeeRepository.findById(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Employee", targetId));
            boolean isTheirManager = target.getManager() != null && target.getManager().getId().equals(user.getId());
            if (!isTheirManager) {
                throw new AccessDeniedException("You can only view the balance of your own team");
            }
        }
        return getBalances(targetId, year != null ? year : LocalDate.now(clock).getYear());
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> getBalances(Long employeeId, int year) {
        List<LeaveUsage> usage = usageInYear(employeeId, year, NO_EXCLUSION);

        return leaveTypeRepository.findAllByOrderByIdAsc().stream()
                .map(type -> {
                    int used = daysFor(usage, type.getId(), LeaveStatus.APPROVED);
                    int pending = daysFor(usage, type.getId(), LeaveStatus.PENDING);
                    Integer available = type.hasQuota() ? type.getAnnualQuota() - used - pending : null;
                    return new LeaveBalanceResponse(type.getId(), type.getCode(), type.getName(), type.getColor(),
                            type.getAnnualQuota(), used, pending, available);
                })
                .toList();
    }

    /**
     * Days still available for one leave type, ignoring the request being edited.
     *
     * @return null when the leave type has no quota (unlimited)
     */
    @Transactional(readOnly = true)
    public Integer getAvailableDays(Long employeeId, LeaveType type, int year, long excludeRequestId) {
        if (!type.hasQuota()) {
            return null;
        }
        List<LeaveUsage> usage = usageInYear(employeeId, year, excludeRequestId);
        int taken = daysFor(usage, type.getId(), LeaveStatus.APPROVED) + daysFor(usage, type.getId(), LeaveStatus.PENDING);
        return type.getAnnualQuota() - taken;
    }

    private List<LeaveUsage> usageInYear(Long employeeId, int year, long excludeRequestId) {
        return leaveRequestRepository.sumWorkingDays(employeeId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31),
                Set.of(LeaveStatus.APPROVED, LeaveStatus.PENDING), excludeRequestId);
    }

    private static int daysFor(List<LeaveUsage> usage, Long leaveTypeId, LeaveStatus status) {
        return usage.stream()
                .filter(u -> u.leaveTypeId().equals(leaveTypeId) && u.status() == status)
                .mapToInt(u -> u.totalDays().intValue())
                .sum();
    }
}
