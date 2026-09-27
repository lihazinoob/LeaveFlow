package com.misl.leaveflow.leave;

import com.misl.leaveflow.common.dto.PageResponse;
import com.misl.leaveflow.leave.dto.LeaveApplyRequest;
import com.misl.leaveflow.leave.dto.LeaveBalanceResponse;
import com.misl.leaveflow.leave.dto.LeaveDecisionRequest;
import com.misl.leaveflow.leave.dto.LeaveHistoryResponse;
import com.misl.leaveflow.leave.dto.LeavePreviewResponse;
import com.misl.leaveflow.leave.dto.LeaveRequestResponse;
import com.misl.leaveflow.security.EmployeeUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Leave requests")
@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private final LeaveWorkflowService workflowService;
    private final LeaveBalanceService balanceService;

    // ---------- applying ----------

    @Operation(summary = "Preview working days, balance and rule violations before submitting")
    @GetMapping("/preview")
    public LeavePreviewResponse preview(@AuthenticationPrincipal EmployeeUserDetails user,
                                        @RequestParam Long leaveTypeId,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                        @RequestParam(required = false) Long excludeRequestId) {
        return leaveRequestService.preview(user, leaveTypeId, startDate, endDate, excludeRequestId);
    }

    @Operation(summary = "Apply for leave")
    @PostMapping
    public ResponseEntity<LeaveRequestResponse> apply(@AuthenticationPrincipal EmployeeUserDetails user,
                                                      @Valid @RequestBody LeaveApplyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leaveRequestService.apply(user, request));
    }

    @Operation(summary = "Edit your own pending request")
    @PutMapping("/{id}")
    public LeaveRequestResponse update(@AuthenticationPrincipal EmployeeUserDetails user, @PathVariable Long id,
                                       @Valid @RequestBody LeaveApplyRequest request) {
        return leaveRequestService.update(user, id, request);
    }

    // ---------- workflow ----------

    @Operation(summary = "Approve a pending request (manager of the employee, or admin)")
    @PostMapping("/{id}/approve")
    public LeaveRequestResponse approve(@AuthenticationPrincipal EmployeeUserDetails user, @PathVariable Long id,
                                        @Valid @RequestBody(required = false) LeaveDecisionRequest request) {
        return workflowService.approve(user, id, commentOf(request));
    }

    @Operation(summary = "Reject a pending request; a comment is required")
    @PostMapping("/{id}/reject")
    public LeaveRequestResponse reject(@AuthenticationPrincipal EmployeeUserDetails user, @PathVariable Long id,
                                       @Valid @RequestBody(required = false) LeaveDecisionRequest request) {
        return workflowService.reject(user, id, commentOf(request));
    }

    @Operation(summary = "Cancel a request (owner or admin; approved leave only before it starts)")
    @PostMapping("/{id}/cancel")
    public LeaveRequestResponse cancel(@AuthenticationPrincipal EmployeeUserDetails user, @PathVariable Long id,
                                       @Valid @RequestBody(required = false) LeaveDecisionRequest request) {
        return workflowService.cancel(user, id, commentOf(request));
    }

    // ---------- reading ----------

    @Operation(summary = "My leave requests")
    @GetMapping("/my")
    public List<LeaveRequestResponse> myRequests(@AuthenticationPrincipal EmployeeUserDetails user,
                                                 @RequestParam(required = false) LeaveStatus status,
                                                 @RequestParam(required = false) Integer year) {
        return leaveRequestService.getMyRequests(user, status, year);
    }

    @Operation(summary = "Leave requests of my direct reports")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/team")
    public List<LeaveRequestResponse> teamRequests(@AuthenticationPrincipal EmployeeUserDetails user,
                                                   @RequestParam(required = false) LeaveStatus status,
                                                   @RequestParam(required = false) Integer year) {
        return leaveRequestService.getTeamRequests(user, status, year);
    }

    @Operation(summary = "Pending requests waiting for my decision")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/approvals")
    public List<LeaveRequestResponse> pendingApprovals(@AuthenticationPrincipal EmployeeUserDetails user) {
        return leaveRequestService.getPendingApprovals(user);
    }

    @Operation(summary = "All leave requests (admin), filtered and paged")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public PageResponse<LeaveRequestResponse> search(
            @AuthenticationPrincipal EmployeeUserDetails user,
            @RequestParam(required = false) LeaveStatus status,
            @RequestParam(required = false) Long leaveTypeId,
            @RequestParam(required = false) String employee,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject
            @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return leaveRequestService.search(user, status, leaveTypeId, employee, from, to, pageable);
    }

    @Operation(summary = "One leave request (owner, their manager, or admin)")
    @GetMapping("/{id}")
    public LeaveRequestResponse getById(@AuthenticationPrincipal EmployeeUserDetails user, @PathVariable Long id) {
        return leaveRequestService.getById(user, id);
    }

    @Operation(summary = "Audit trail of one leave request")
    @GetMapping("/{id}/history")
    public List<LeaveHistoryResponse> history(@AuthenticationPrincipal EmployeeUserDetails user,
                                              @PathVariable Long id) {
        return leaveRequestService.getHistory(user, id);
    }

    @Operation(summary = "Leave balance per type. Without employeeId: my own. With it: admin, or that person's manager.")
    @GetMapping("/balance")
    public List<LeaveBalanceResponse> balance(@AuthenticationPrincipal EmployeeUserDetails user,
                                              @RequestParam(required = false) Long employeeId,
                                              @RequestParam(required = false) Integer year) {
        return balanceService.getBalancesFor(user, employeeId, year);
    }

    private static String commentOf(LeaveDecisionRequest request) {
        return request != null ? request.comment() : null;
    }
}
