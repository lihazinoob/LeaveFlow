package com.misl.leaveflow.demo;

import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.EmployeeRepository;
import com.misl.leaveflow.employee.Role;
import com.misl.leaveflow.leave.LeaveRequest;
import com.misl.leaveflow.leave.LeaveRequestHistory;
import com.misl.leaveflow.leave.LeaveRequestHistoryRepository;
import com.misl.leaveflow.leave.LeaveRequestRepository;
import com.misl.leaveflow.leave.LeaveStatus;
import com.misl.leaveflow.leave.WorkingDayCalculator;
import com.misl.leaveflow.leave.type.LeaveTypeRepository;
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

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DemoDataLoaderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private LeaveRequestHistoryRepository historyRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private LeaveTypeRepository leaveTypeRepository;
    @Mock
    private WorkingDayCalculator workingDayCalculator;

    private DemoDataLoader loader;
    private final List<LeaveRequest> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Dhaka")).toInstant(), ZoneId.of("Asia/Dhaka"));
        loader = new DemoDataLoader(leaveRequestRepository, historyRepository, employeeRepository, leaveTypeRepository,
                workingDayCalculator, clock);

        Employee admin = TestEmployees.employee(1L, "admin@leaveflow.com", Role.ADMIN);
        List<Employee> people = new ArrayList<>(List.of(admin));
        String[] managers = {"tanvir.ahmed", "farhana.rahman"};
        String[] staff = {"rakib.hasan", "sadia.islam", "mahmud.hossain", "nabila.chowdhury", "arif.khan",
                "tasnim.akter", "imran.kabir"};
        long id = 2;
        for (String name : managers) {
            Employee manager = TestEmployees.employee(id++, name + "@leaveflow.com", Role.MANAGER);
            manager.setManager(admin);
            people.add(manager);
        }
        for (String name : staff) {
            Employee employee = TestEmployees.employee(id++, name + "@leaveflow.com", Role.EMPLOYEE);
            employee.setManager(people.get(1));
            people.add(employee);
        }
        when(employeeRepository.findAll()).thenReturn(people);
        when(leaveTypeRepository.findAll()).thenReturn(List.of(
                TestLeaves.leaveType(1L, "ANNUAL", "Annual Leave", 15), TestLeaves.leaveType(2L, "SICK", "Sick Leave", 14),
                TestLeaves.leaveType(3L, "CASUAL", "Casual Leave", 10), TestLeaves.unpaidLeave()));
        when(leaveRequestRepository.save(any())).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
    }

    @Test
    void doesNothingWhenLeaveRequestsAlreadyExist() {
        when(leaveRequestRepository.count()).thenReturn(3L);

        loader.run(null);

        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void loadsLeaveRelativeToToday_withReviewersAndHistory() {
        when(leaveRequestRepository.count()).thenReturn(0L);
        when(workingDayCalculator.countWorkingDays(any(), any())).thenReturn(2);

        loader.run(null);

        assertThat(saved).hasSize(19);
        assertThat(saved).anySatisfy(leave -> {
            assertThat(leave.getStartDate()).isBeforeOrEqualTo(TODAY);
            assertThat(leave.getEndDate()).isAfterOrEqualTo(TODAY);
            assertThat(leave.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        });
        assertThat(saved).filteredOn(leave -> leave.getStatus() == LeaveStatus.APPROVED)
                .allSatisfy(leave -> assertThat(leave.getReviewedBy()).isNotNull());
        assertThat(saved).filteredOn(leave -> leave.getStatus() == LeaveStatus.PENDING)
                .allSatisfy(leave -> assertThat(leave.getReviewedBy()).isNull());

        ArgumentCaptor<LeaveRequestHistory> history = ArgumentCaptor.forClass(LeaveRequestHistory.class);
        verify(historyRepository, org.mockito.Mockito.atLeast(19)).save(history.capture());
        long pendingCount = saved.stream().filter(l -> l.getStatus() == LeaveStatus.PENDING).count();
        assertThat(history.getAllValues()).hasSize((int) (19 + (19 - pendingCount)));
    }

    @Test
    void rangeOnlyOnWeekend_isStretchedToContainAWorkingDay() {
        when(leaveRequestRepository.count()).thenReturn(0L);
        // First call for every leave reports 0 working days, the second (after stretching) reports 1.
        when(workingDayCalculator.countWorkingDays(any(), any())).thenReturn(0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1,
                0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1);

        loader.run(null);

        assertThat(saved).isNotEmpty().allSatisfy(leave -> assertThat(leave.getWorkingDays()).isEqualTo(1));
    }
}
