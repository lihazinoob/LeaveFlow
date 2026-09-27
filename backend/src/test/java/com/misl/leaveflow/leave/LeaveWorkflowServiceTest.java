package com.misl.leaveflow.leave;

import com.misl.leaveflow.common.exception.BusinessRuleException;
import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.EmployeeRepository;
import com.misl.leaveflow.employee.Role;
import com.misl.leaveflow.leave.dto.LeaveRequestResponse;
import com.misl.leaveflow.security.EmployeeUserDetails;
import com.misl.leaveflow.support.TestEmployees;
import com.misl.leaveflow.support.TestLeaves;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** "Today" is fixed to Sunday 27 Sep 2026. Hierarchy: admin -> manager (Tanvir) -> Rakib. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaveWorkflowServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private LeaveRequestHistoryRepository historyRepository;
    @Mock
    private EmployeeRepository employeeRepository;

    private LeaveWorkflowService service;

    private final Employee admin = TestEmployees.employee(1L, "admin@leaveflow.com", Role.ADMIN);
    private final Employee manager = TestEmployees.employee(2L, "tanvir@leaveflow.com", Role.MANAGER);
    private final Employee otherManager = TestEmployees.employee(3L, "farhana@leaveflow.com", Role.MANAGER);
    private final Employee rakib = TestEmployees.employee(7L, "rakib@leaveflow.com", Role.EMPLOYEE);
    private final Employee sadia = TestEmployees.employee(8L, "sadia@leaveflow.com", Role.EMPLOYEE);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Dhaka")).toInstant(), ZoneId.of("Asia/Dhaka"));
        service = new LeaveWorkflowService(leaveRequestRepository, historyRepository, employeeRepository,
                new LeavePermissions(clock));
        manager.setManager(admin);
        rakib.setManager(manager);
        sadia.setManager(manager);
        for (Employee e : List.of(admin, manager, otherManager, rakib, sadia)) {
            when(employeeRepository.getReferenceById(e.getId())).thenReturn(e);
        }
    }

    // ---------- approve / reject ----------

    @Test
    void approve_byDirectManager_setsReviewerAndWritesHistory() {
        LeaveRequest leave = stored(LeaveStatus.PENDING, rakib, TODAY.plusDays(5));

        LeaveRequestResponse response = service.approve(user(manager), 50L, " Enjoy ");

        assertThat(leave.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(leave.getReviewedBy()).isSameAs(manager);
        assertThat(leave.getReviewComment()).isEqualTo("Enjoy");
        assertThat(leave.getReviewedAt()).isNotNull();
        assertThat(response.canReview()).isFalse();
        LeaveRequestHistory entry = savedHistory();
        assertThat(entry.getFromStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(entry.getToStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(entry.getChangedBy()).isSameAs(manager);
        verify(leaveRequestRepository).saveAndFlush(leave);
    }

    @Test
    void approve_byAdmin_isAllowedForAnyone() {
        LeaveRequest leave = stored(LeaveStatus.PENDING, rakib, TODAY.plusDays(5));
        service.approve(user(admin), 50L, null);
        assertThat(leave.getStatus()).isEqualTo(LeaveStatus.APPROVED);
    }

    @Test
    void approve_byManagerOfAnotherTeam_isForbidden() {
        stored(LeaveStatus.PENDING, rakib, TODAY.plusDays(5));
        assertThatThrownBy(() -> service.approve(user(otherManager), 50L, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void approve_byColleague_isForbidden() {
        stored(LeaveStatus.PENDING, rakib, TODAY.plusDays(5));
        assertThatThrownBy(() -> service.approve(user(sadia), 50L, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void approve_ownRequest_isForbiddenEvenForAdmin() {
        stored(LeaveStatus.PENDING, admin, TODAY.plusDays(5));
        assertThatThrownBy(() -> service.approve(user(admin), 50L, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void approve_alreadyRejectedRequest_isRejectedByStateMachine() {
        stored(LeaveStatus.REJECTED, rakib, TODAY.plusDays(5));
        assertThatThrownBy(() -> service.approve(user(manager), 50L, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A request that is rejected cannot be approved");
        verify(historyRepository, never()).save(any());
    }

    @Test
    void reject_requiresComment() {
        stored(LeaveStatus.PENDING, rakib, TODAY.plusDays(5));
        assertThatThrownBy(() -> service.reject(user(manager), 50L, "  "))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A comment is required when rejecting a request");
    }

    @Test
    void reject_withComment_recordsDecision() {
        LeaveRequest leave = stored(LeaveStatus.PENDING, rakib, TODAY.plusDays(5));
        service.reject(user(manager), 50L, "Release week, please pick other dates");
        assertThat(leave.getStatus()).isEqualTo(LeaveStatus.REJECTED);
        assertThat(savedHistory().getComment()).isEqualTo("Release week, please pick other dates");
    }

    // ---------- cancel ----------

    @Test
    void cancel_pendingRequest_byOwner() {
        LeaveRequest leave = stored(LeaveStatus.PENDING, rakib, TODAY.plusDays(5));
        service.cancel(user(rakib), 50L, null);
        assertThat(leave.getStatus()).isEqualTo(LeaveStatus.CANCELLED);
    }

    @Test
    void cancel_approvedFutureLeave_byOwner() {
        LeaveRequest leave = stored(LeaveStatus.APPROVED, rakib, TODAY.plusDays(1));
        service.cancel(user(rakib), 50L, "Plans changed");
        assertThat(leave.getStatus()).isEqualTo(LeaveStatus.CANCELLED);
    }

    @Test
    void cancel_approvedLeaveThatAlreadyStarted_isRejected() {
        stored(LeaveStatus.APPROVED, rakib, TODAY);
        assertThatThrownBy(() -> service.cancel(user(rakib), 50L, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("An approved leave can only be cancelled before it starts");
    }

    @Test
    void cancel_byManager_isForbidden_butAdminMay() {
        LeaveRequest leave = stored(LeaveStatus.PENDING, rakib, TODAY.plusDays(5));
        assertThatThrownBy(() -> service.cancel(user(manager), 50L, null)).isInstanceOf(AccessDeniedException.class);

        service.cancel(user(admin), 50L, "Duplicate request");
        assertThat(leave.getStatus()).isEqualTo(LeaveStatus.CANCELLED);
    }

    @Test
    void cancel_alreadyCancelled_isRejected() {
        stored(LeaveStatus.CANCELLED, rakib, TODAY.plusDays(5));
        assertThatThrownBy(() -> service.cancel(user(rakib), 50L, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A request that is cancelled cannot be cancelled");
    }

    // ---------- deactivation hook ----------

    @Test
    void cancelPendingRequestsOf_cancelsEachWithHistory() {
        LeaveRequest first = TestLeaves.request(51L, rakib, LeaveStatus.PENDING, TODAY.plusDays(3), TODAY.plusDays(4));
        LeaveRequest second = TestLeaves.request(52L, rakib, LeaveStatus.PENDING, TODAY.plusDays(9), TODAY.plusDays(9));
        when(leaveRequestRepository.findByEmployeeIdAndStatus(7L, LeaveStatus.PENDING)).thenReturn(List.of(first, second));

        int cancelled = service.cancelPendingRequestsOf(7L, 1L);

        assertThat(cancelled).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(LeaveStatus.CANCELLED);
        assertThat(second.getStatus()).isEqualTo(LeaveStatus.CANCELLED);
        verify(historyRepository, times(2)).save(any(LeaveRequestHistory.class));
    }

    // ---------- helpers ----------

    private LeaveRequest stored(LeaveStatus status, Employee owner, LocalDate start) {
        LeaveRequest leave = TestLeaves.request(50L, owner, status, start, start.plusDays(1));
        when(leaveRequestRepository.findWithDetailsById(anyLong())).thenReturn(Optional.of(leave));
        return leave;
    }

    private LeaveRequestHistory savedHistory() {
        ArgumentCaptor<LeaveRequestHistory> captor = ArgumentCaptor.forClass(LeaveRequestHistory.class);
        verify(historyRepository).save(captor.capture());
        return captor.getValue();
    }

    private static EmployeeUserDetails user(Employee employee) {
        return EmployeeUserDetails.fromEmployee(employee);
    }
}
