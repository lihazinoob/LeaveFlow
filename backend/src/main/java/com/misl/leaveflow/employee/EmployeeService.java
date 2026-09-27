package com.misl.leaveflow.employee;

import com.misl.leaveflow.auth.refresh.RefreshTokenService;
import com.misl.leaveflow.auth.refresh.RevokedReason;
import com.misl.leaveflow.common.dto.PageResponse;
import com.misl.leaveflow.common.exception.BusinessRuleException;
import com.misl.leaveflow.common.exception.ConflictException;
import com.misl.leaveflow.common.exception.ResourceNotFoundException;
import com.misl.leaveflow.employee.dto.EmployeeCreateRequest;
import com.misl.leaveflow.employee.dto.EmployeeOption;
import com.misl.leaveflow.employee.dto.EmployeeResponse;
import com.misl.leaveflow.employee.dto.EmployeeUpdateRequest;
import com.misl.leaveflow.leave.LeaveWorkflowService;
import com.misl.leaveflow.security.EmployeeUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Employee management: search, create, update, deactivate/reactivate, plus team and dropdown lookups.
 * <p>
 * Rules enforced here:
 * <ul>
 *   <li>Emails are unique (stored in lower case).</li>
 *   <li>EMPLOYEE and MANAGER must report to an active MANAGER or ADMIN; the reporting line cannot loop.</li>
 *   <li>Someone with active direct reports cannot be demoted to EMPLOYEE or deactivated until the team is reassigned.</li>
 *   <li>An admin cannot change their own role or deactivate themselves (prevents locking everyone out).</li>
 *   <li>Deactivation is a soft delete: history is kept, all refresh tokens are revoked so the person is signed out,
 *       and their pending leave requests are cancelled.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final LeaveWorkflowService leaveWorkflowService;

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> search(String search, String department, Role role, Boolean active,
                                                 Pageable pageable) {
        List<Specification<Employee>> filters = new ArrayList<>();
        if (StringUtils.hasText(search)) {
            filters.add(EmployeeSpecifications.matchesSearch(search));
        }
        if (StringUtils.hasText(department)) {
            filters.add(EmployeeSpecifications.hasDepartment(department));
        }
        if (role != null) {
            filters.add(EmployeeSpecifications.hasRole(role));
        }
        if (active != null) {
            filters.add(EmployeeSpecifications.hasActive(active));
        }

        return PageResponse.from(employeeRepository.findAll(Specification.allOf(filters), pageable)
                .map(EmployeeResponse::from));
    }

    /** Admins can view anyone; managers can view themselves and their direct reports. */
    @Transactional(readOnly = true)
    public EmployeeResponse getById(Long id, EmployeeUserDetails currentUser) {
        Employee employee = findEmployee(id);
        if (currentUser.getRole() != Role.ADMIN && !isSelfOrDirectReport(employee, currentUser.getId())) {
            throw new AccessDeniedException("Managers can only view their own team members");
        }
        return EmployeeResponse.from(employee);
    }

    @Transactional
    public EmployeeResponse create(EmployeeCreateRequest request) {
        String email = normaliseEmail(request.email());
        if (employeeRepository.existsByEmail(email)) {
            throw new ConflictException("An employee with email " + email + " already exists");
        }

        Employee employee = new Employee();
        employee.setFullName(request.fullName().trim());
        employee.setEmail(email);
        employee.setPasswordHash(passwordEncoder.encode(request.password()));
        employee.setRole(request.role());
        employee.setDepartment(request.department().trim());
        employee.setDesignation(request.designation().trim());
        employee.setManager(resolveManager(request.managerId(), request.role(), null));
        employee.setJoiningDate(request.joiningDate());

        return EmployeeResponse.from(employeeRepository.save(employee));
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeUpdateRequest request, EmployeeUserDetails currentUser) {
        Employee employee = findEmployee(id);
        String email = normaliseEmail(request.email());

        if (employeeRepository.existsByEmailAndIdNot(email, id)) {
            throw new ConflictException("An employee with email " + email + " already exists");
        }
        if (id.equals(currentUser.getId()) && request.role() != employee.getRole()) {
            throw new BusinessRuleException("You cannot change your own role");
        }
        if (request.role() == Role.EMPLOYEE && employee.getRole() != Role.EMPLOYEE
                && employeeRepository.existsByManagerIdAndActiveTrue(id)) {
            throw new BusinessRuleException(
                    "This person still manages active employees. Reassign their team before changing the role.");
        }

        employee.setFullName(request.fullName().trim());
        employee.setEmail(email);
        employee.setRole(request.role());
        employee.setDepartment(request.department().trim());
        employee.setDesignation(request.designation().trim());
        employee.setManager(resolveManager(request.managerId(), request.role(), id));
        employee.setJoiningDate(request.joiningDate());

        return EmployeeResponse.from(employee);
    }

    /** Soft delete: the row stays for leave history, but the person can no longer log in or refresh. */
    @Transactional
    public void deactivate(Long id, EmployeeUserDetails currentUser) {
        if (id.equals(currentUser.getId())) {
            throw new BusinessRuleException("You cannot deactivate your own account");
        }
        Employee employee = findEmployee(id);
        if (!employee.isActive()) {
            return;
        }
        if (employeeRepository.existsByManagerIdAndActiveTrue(id)) {
            throw new BusinessRuleException(
                    "This person still manages active employees. Reassign their team before deactivating.");
        }

        employee.setActive(false);
        refreshTokenService.revokeAllForEmployee(id, RevokedReason.ACCOUNT_DEACTIVATED);
        leaveWorkflowService.cancelPendingRequestsOf(id, currentUser.getId());
    }

    @Transactional
    public EmployeeResponse activate(Long id) {
        Employee employee = findEmployee(id);
        employee.setActive(true);
        return EmployeeResponse.from(employee);
    }

    /** Active direct reports of the given employee (a manager's team). */
    @Transactional(readOnly = true)
    public List<EmployeeResponse> getTeam(Long managerId) {
        return employeeRepository.findByManagerIdAndActiveTrueOrderByFullNameAsc(managerId).stream()
                .map(EmployeeResponse::from)
                .toList();
    }

    /** Active people who can be chosen as someone's manager. */
    @Transactional(readOnly = true)
    public List<EmployeeOption> getManagerOptions() {
        return employeeRepository.findByRoleInAndActiveTrueOrderByFullNameAsc(List.of(Role.MANAGER, Role.ADMIN))
                .stream()
                .map(EmployeeOption::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> getDepartments() {
        return employeeRepository.findDistinctDepartments();
    }

    /**
     * Checks the chosen manager and returns it.
     *
     * @param employeeId the employee being edited, or null when creating a new one
     */
    private Employee resolveManager(Long managerId, Role role, Long employeeId) {
        if (managerId == null) {
            if (role != Role.ADMIN) {
                throw new BusinessRuleException("A manager is required for employees and managers");
            }
            return null;
        }
        if (managerId.equals(employeeId)) {
            throw new BusinessRuleException("An employee cannot be their own manager");
        }

        Employee manager = employeeRepository.findById(managerId)
                .orElseThrow(() -> new BusinessRuleException("The selected manager does not exist"));
        if (!manager.isActive()) {
            throw new BusinessRuleException("The selected manager is deactivated");
        }
        if (manager.getRole() == Role.EMPLOYEE) {
            throw new BusinessRuleException("The selected manager must have the MANAGER or ADMIN role");
        }
        if (employeeId != null) {
            ensureNoReportingLoop(manager, employeeId);
        }
        return manager;
    }

    /** Walks up the chosen manager's chain; finding the edited employee there would create a loop. */
    private void ensureNoReportingLoop(Employee manager, Long employeeId) {
        for (Employee current = manager; current != null; current = current.getManager()) {
            if (employeeId.equals(current.getId())) {
                throw new BusinessRuleException("This manager reports to the employee, which would create a loop");
            }
        }
    }

    private boolean isSelfOrDirectReport(Employee employee, Long currentUserId) {
        return employee.getId().equals(currentUserId)
                || (employee.getManager() != null && Objects.equals(employee.getManager().getId(), currentUserId));
    }

    private Employee findEmployee(Long id) {
        return employeeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Employee", id));
    }

    private static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
