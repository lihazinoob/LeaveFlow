package com.misl.leaveflow.employee;

import com.misl.leaveflow.common.dto.PageResponse;
import com.misl.leaveflow.employee.dto.EmployeeCreateRequest;
import com.misl.leaveflow.employee.dto.EmployeeOption;
import com.misl.leaveflow.employee.dto.EmployeeResponse;
import com.misl.leaveflow.employee.dto.EmployeeUpdateRequest;
import com.misl.leaveflow.security.EmployeeUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Employee management. Most operations are ADMIN-only; managers may read their own team.
 * Role checks use @PreAuthorize (method security), and ownership checks happen in the service.
 */
@Tag(name = "Employees")
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @Operation(summary = "Search employees (paged). Sort e.g. ?sort=fullName,asc")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public PageResponse<EmployeeResponse> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active,
            @ParameterObject
            @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable) {
        return employeeService.search(search, department, role, active, pageable);
    }

    @Operation(summary = "Active direct reports of the logged-in manager")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/team")
    public List<EmployeeResponse> team(@AuthenticationPrincipal EmployeeUserDetails currentUser) {
        return employeeService.getTeam(currentUser.getId());
    }

    @Operation(summary = "Active managers and admins, for the 'manager' dropdown")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/manager-options")
    public List<EmployeeOption> managerOptions() {
        return employeeService.getManagerOptions();
    }

    @Operation(summary = "Distinct department names, for filters and form suggestions")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/departments")
    public List<String> departments() {
        return employeeService.getDepartments();
    }

    @Operation(summary = "Get one employee (admins: anyone, managers: their team)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/{id}")
    public EmployeeResponse getById(@PathVariable Long id,
                                    @AuthenticationPrincipal EmployeeUserDetails currentUser) {
        return employeeService.getById(id, currentUser);
    }

    @Operation(summary = "Create an employee")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody EmployeeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.create(request));
    }

    @Operation(summary = "Update an employee")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public EmployeeResponse update(@PathVariable Long id,
                                   @Valid @RequestBody EmployeeUpdateRequest request,
                                   @AuthenticationPrincipal EmployeeUserDetails currentUser) {
        return employeeService.update(id, request, currentUser);
    }

    @Operation(summary = "Deactivate an employee (soft delete; signs them out)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id,
                                           @AuthenticationPrincipal EmployeeUserDetails currentUser) {
        employeeService.deactivate(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reactivate a deactivated employee")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/activate")
    public EmployeeResponse activate(@PathVariable Long id) {
        return employeeService.activate(id);
    }
}
