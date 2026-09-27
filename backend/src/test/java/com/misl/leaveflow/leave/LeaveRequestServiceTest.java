package com.misl.leaveflow.leave;

import com.misl.leaveflow.common.exception.BusinessRuleException;
import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.EmployeeRepository;
import com.misl.leaveflow.employee.Role;
import com.misl.leaveflow.leave.dto.LeaveApplyRequest;
import com.misl.leaveflow.leave.dto.LeavePreviewResponse;
import com.misl.leaveflow.leave.dto.LeaveRequestResponse;
import com.misl.leaveflow.leave.type.LeaveType;
import com.misl.leaveflow.leave.type.LeaveTypeRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** "Today" is fixed to Sunday 27 Sep 2026 (Dhaka time). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaveRequestServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private LeaveRequestHistoryRepository historyRepository;
    @Mock
    private LeaveTypeRepository leaveTypeRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private WorkingDayCalculator workingDayCalculator;
    @Mock
    private LeaveBalanceService balanceService;

    private LeaveRequestService service;

    private final Employee manager = TestEmployees.employee(2L, "tanvir@leaveflow.com", Role.MANAGER);
    private final Employee rakib = TestEmployees.employee(7L, "rakib@leaveflow.com", Role.EMPLOYEE);
    private final EmployeeUserDetails rakibUser = EmployeeUserDetails.fromEmployee(rakib);
    private final LeaveType annual = TestLeaves.annualLeave();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Dhaka")).toInstant(), ZoneId.of("Asia/Dhaka"));
        service = new LeaveRequestService(leaveRequestRepository, historyRepository, leaveTypeRepository,
                employeeRepository, workingDayCalculator, balanceService, new LeavePermissions(clock), clock);
        rakib.setManager(manager);

        when(employeeRepository.findById(7L)).thenReturn(Optional.of(rakib));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(annual));
        when(workingDayCalculator.countWorkingDays(any(), any())).thenReturn(2);
        when(leaveRequestRepository.existsOverlapping(anyLong(), any(), any(), any(), anyLong())).thenReturn(false);
        when(balanceService.getAvailableDays(anyLong(), any(), eq(2026), anyLong())).thenReturn(15);
    }

    // ---------- apply ----------

    @Test
    void apply_validRequest_isSavedAsPendingWithWorkingDaysAndHistory() {
        LeaveRequestResponse response = service.apply(rakibUser, request(TODAY.plusDays(4), TODAY.plusDays(7)));

        ArgumentCaptor<LeaveRequest> saved = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(saved.getValue().getWorkingDays()).isEqualTo(2);
        assertThat(saved.getValue().getEmployee()).isSameAs(rakib);

        ArgumentCaptor<LeaveRequestHistory> history = ArgumentCaptor.forClass(LeaveRequestHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getFromStatus()).isNull();
        assertThat(history.getValue().getToStatus()).isEqualTo(LeaveStatus.PENDING);

        assertThat(response.canEdit()).isTrue();
        assertThat(response.canCancel()).isTrue();
        assertThat(response.canReview()).isFalse();
    }

    @Test
    void apply_startingToday_isAllowed() {
        service.apply(rakibUser, request(TODAY, TODAY));
        verify(leaveRequestRepository).save(any());
    }

    @Test
    void apply_endBeforeStart_isRejected() {
        assertRejected(request(TODAY.plusDays(5), TODAY.plusDays(2)), "End date must be on or after the start date");
    }

    @Test
    void apply_inThePast_isRejected() {
        assertRejected(request(TODAY.minusDays(1), TODAY.plusDays(1)), "Leave cannot start in the past");
    }

    @Test
    void apply_acrossTwoYears_isRejected() {
        assertRejected(request(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 3)), "cannot span two calendar years");
    }

    @Test
    void apply_onlyWeekendDays_isRejected() {
        when(workingDayCalculator.countWorkingDays(any(), any())).thenReturn(0);
        assertRejected(request(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3)), "no working days");
    }

    @Test
    void apply_overlappingExistingLeave_isRejected() {
        when(leaveRequestRepository.existsOverlapping(eq(7L), any(), any(), any(), eq(-1L))).thenReturn(true);
        assertRejected(request(TODAY.plusDays(4), TODAY.plusDays(7)), "overlaps");
    }

    @Test
    void apply_moreDaysThanBalance_isRejected() {
        when(balanceService.getAvailableDays(anyLong(), any(), eq(2026), anyLong())).thenReturn(1);
        assertRejected(request(TODAY.plusDays(4), TODAY.plusDays(7)),
                "Not enough Annual Leave balance: 2 day(s) requested, 1 available");
    }

    @Test
    void apply_unlimitedLeaveType_skipsBalanceCheck() {
        LeaveType unpaid = TestLeaves.unpaidLeave();
        when(leaveTypeRepository.findById(4L)).thenReturn(Optional.of(unpaid));
        when(balanceService.getAvailableDays(anyLong(), eq(unpaid), eq(2026), anyLong())).thenReturn(null);

        service.apply(rakibUser, new LeaveApplyRequest(4L, TODAY.plusDays(4), TODAY.plusDays(7), "Travel"));

        verify(leaveRequestRepository).save(any());
    }

    @Test
    void preview_reportsEveryProblemAtOnce_withoutSaving() {
        when(leaveRequestRepository.existsOverlapping(anyLong(), any(), any(), any(), anyLong())).thenReturn(true);
        when(balanceService.getAvailableDays(anyLong(), any(), eq(2026), anyLong())).thenReturn(0);

        LeavePreviewResponse preview = service.preview(rakibUser, 1L, TODAY.minusDays(2), TODAY.plusDays(2), null);

        assertThat(preview.valid()).isFalse();
        assertThat(preview.workingDays()).isEqualTo(2);
        assertThat(preview.availableDays()).isZero();
        assertThat(preview.problems()).hasSize(3);
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void preview_validRequest_hasNoProblems() {
        LeavePreviewResponse preview = service.preview(rakibUser, 1L, TODAY.plusDays(4), TODAY.plusDays(7), null);

        assertThat(preview.valid()).isTrue();
        assertThat(preview.problems()).isEmpty();
        assertThat(preview.availableDays()).isEqualTo(15);
    }

    // ---------- update ----------

    @Test
    void update_pendingOwnRequest_recalculatesAndExcludesItselfFromChecks() {
        LeaveRequest existing = TestLeaves.request(50L, rakib, LeaveStatus.PENDING, TODAY.plusDays(4), TODAY.plusDays(5));
        when(leaveRequestRepository.findWithDetailsById(50L)).thenReturn(Optional.of(existing));
        when(workingDayCalculator.countWorkingDays(any(), any())).thenReturn(3);

        service.update(rakibUser, 50L, request(TODAY.plusDays(4), TODAY.plusDays(8)));

        assertThat(existing.getWorkingDays()).isEqualTo(3);
        assertThat(existing.getEndDate()).isEqualTo(TODAY.plusDays(8));
        verify(leaveRequestRepository).existsOverlapping(eq(7L), any(), any(), any(), eq(50L));
        verify(balanceService).getAvailableDays(eq(7L), any(), eq(2026), eq(50L));
    }

    @Test
    void update_someoneElsesRequest_isForbidden() {
        LeaveRequest existing = TestLeaves.request(50L, manager, LeaveStatus.PENDING, TODAY.plusDays(4), TODAY.plusDays(5));
        when(leaveRequestRepository.findWithDetailsById(50L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(rakibUser, 50L, request(TODAY.plusDays(4), TODAY.plusDays(5))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void update_approvedRequest_isRejected() {
        LeaveRequest existing = TestLeaves.request(50L, rakib, LeaveStatus.APPROVED, TODAY.plusDays(4), TODAY.plusDays(5));
        when(leaveRequestRepository.findWithDetailsById(50L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(rakibUser, 50L, request(TODAY.plusDays(4), TODAY.plusDays(5))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only pending requests can be edited");
    }

    // ---------- read access ----------

    @Test
    void getById_visibleToOwnerManagerAndAdmin_butNotColleagues() {
        LeaveRequest leave = TestLeaves.request(50L, rakib, LeaveStatus.PENDING, TODAY.plusDays(4), TODAY.plusDays(5));
        when(leaveRequestRepository.findWithDetailsById(50L)).thenReturn(Optional.of(leave));
        Employee colleague = TestEmployees.employee(8L, "sadia@leaveflow.com", Role.EMPLOYEE);
        Employee admin = TestEmployees.employee(1L, "admin@leaveflow.com", Role.ADMIN);

        assertThat(service.getById(rakibUser, 50L).canEdit()).isTrue();
        assertThat(service.getById(EmployeeUserDetails.fromEmployee(manager), 50L).canReview()).isTrue();
        assertThat(service.getById(EmployeeUserDetails.fromEmployee(admin), 50L).canReview()).isTrue();
        assertThatThrownBy(() -> service.getById(EmployeeUserDetails.fromEmployee(colleague), 50L))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------- helpers ----------

    private LeaveApplyRequest request(LocalDate start, LocalDate end) {
        return new LeaveApplyRequest(1L, start, end, "  Family event  ");
    }

    private void assertRejected(LeaveApplyRequest request, String expectedMessagePart) {
        assertThatThrownBy(() -> service.apply(rakibUser, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(expectedMessagePart);
        verify(leaveRequestRepository, never()).save(any());
    }
}
