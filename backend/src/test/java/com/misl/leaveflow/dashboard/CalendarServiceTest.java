package com.misl.leaveflow.dashboard;

import com.misl.leaveflow.dashboard.dto.CalendarResponse;
import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.Role;
import com.misl.leaveflow.holiday.PublicHoliday;
import com.misl.leaveflow.holiday.PublicHolidayRepository;
import com.misl.leaveflow.leave.LeaveProperties;
import com.misl.leaveflow.leave.LeaveStatus;
import com.misl.leaveflow.leave.WorkingDayCalculator;
import com.misl.leaveflow.security.EmployeeUserDetails;
import com.misl.leaveflow.support.TestEmployees;
import com.misl.leaveflow.support.TestLeaves;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalendarServiceTest {

    private static final LocalDate FIRST = LocalDate.of(2026, 10, 1);
    private static final LocalDate LAST = LocalDate.of(2026, 10, 31);

    @Mock
    private LeaveStatsRepository statsRepository;
    @Mock
    private PublicHolidayRepository holidayRepository;

    private CalendarService service;

    private final Employee admin = TestEmployees.employee(1L, "admin@leaveflow.com", Role.ADMIN);
    private final Employee manager = TestEmployees.employee(2L, "tanvir@leaveflow.com", Role.MANAGER);
    private final Employee rakib = TestEmployees.employee(7L, "rakib@leaveflow.com", Role.EMPLOYEE);

    @BeforeEach
    void setUp() {
        WorkingDayCalculator calculator = new WorkingDayCalculator(
                new LeaveProperties(Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)), holidayRepository);
        service = new CalendarService(statsRepository, holidayRepository, calculator);

        PublicHoliday durgaPuja = new PublicHoliday();
        durgaPuja.setDate(LocalDate.of(2026, 10, 20));
        durgaPuja.setName("Durga Puja");
        when(holidayRepository.findByDateBetweenOrderByDateAsc(FIRST, LAST)).thenReturn(List.of(durgaPuja));
    }

    @Test
    void month_hasEveryDayWithWeekendAndHolidayMarkers() {
        when(statsRepository.findInRangeForTeam(any(), any(), any(), any())).thenReturn(List.of());

        CalendarResponse calendar = service.getMonth(EmployeeUserDetails.fromEmployee(manager), YearMonth.of(2026, 10));

        assertThat(calendar.month()).isEqualTo("2026-10");
        assertThat(calendar.days()).hasSize(31);
        CalendarResponse.Day friday2nd = calendar.days().get(1);
        assertThat(friday2nd.date()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(friday2nd.weekend()).isTrue();
        assertThat(calendar.days().get(3).weekend()).as("Sunday is a working day").isFalse();
        assertThat(calendar.days().get(19).holidayName()).isEqualTo("Durga Puja");
        assertThat(calendar.days().get(18).holidayName()).isNull();
    }

    @Test
    void manager_seesTeam_includingPendingLeave() {
        rakib.setManager(manager);
        when(statsRepository.findInRangeForTeam(2L, FIRST, LAST, Set.of(LeaveStatus.PENDING, LeaveStatus.APPROVED)))
                .thenReturn(List.of(TestLeaves.request(9L, rakib, LeaveStatus.PENDING, FIRST.plusDays(3), FIRST.plusDays(5))));

        CalendarResponse calendar = service.getMonth(EmployeeUserDetails.fromEmployee(manager), YearMonth.of(2026, 10));

        assertThat(calendar.scope()).isEqualTo("TEAM");
        assertThat(calendar.absences()).singleElement()
                .satisfies(item -> assertThat(item.status()).isEqualTo(LeaveStatus.PENDING));
        verify(statsRepository, never()).findInRange(any(), any(), any());
    }

    @Test
    void admin_seesEveryone() {
        when(statsRepository.findInRange(any(), any(), any())).thenReturn(List.of());

        CalendarResponse calendar = service.getMonth(EmployeeUserDetails.fromEmployee(admin), YearMonth.of(2026, 10));

        assertThat(calendar.scope()).isEqualTo("ORGANISATION");
        verify(statsRepository).findInRange(FIRST, LAST, Set.of(LeaveStatus.PENDING, LeaveStatus.APPROVED));
    }
}
