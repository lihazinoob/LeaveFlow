package com.misl.leaveflow.leave;

import com.misl.leaveflow.holiday.PublicHoliday;
import com.misl.leaveflow.holiday.PublicHolidayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Calendar used below (Sep/Oct 2026): Sep 24 = Thursday, Sep 25 = Friday, Sep 26 = Saturday, Sep 27 = Sunday. */
@ExtendWith(MockitoExtension.class)
class WorkingDayCalculatorTest {

    @Mock
    private PublicHolidayRepository holidayRepository;

    private WorkingDayCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new WorkingDayCalculator(
                new LeaveProperties(Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)), holidayRepository);
    }

    @Test
    void thursdayToSunday_countsOnlyThursdayAndSunday() {
        noHolidays();
        assertThat(calculator.countWorkingDays(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 27))).isEqualTo(2);
    }

    @Test
    void weekendOnly_isZero() {
        noHolidays();
        assertThat(calculator.countWorkingDays(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26))).isZero();
    }

    @Test
    void singleWorkingDay_isOne() {
        noHolidays();
        LocalDate sunday = LocalDate.of(2026, 9, 27);
        assertThat(calculator.countWorkingDays(sunday, sunday)).isEqualTo(1);
    }

    @Test
    void twoFullWeeks_isTenWorkingDays() {
        noHolidays();
        assertThat(calculator.countWorkingDays(LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 10))).isEqualTo(10);
    }

    @Test
    void publicHolidayInsideRange_isNotCounted() {
        // Sun 18 Oct -> Thu 22 Oct 2026 is 5 working days, minus Durga Puja on Tue 20 Oct.
        PublicHoliday durgaPuja = new PublicHoliday();
        durgaPuja.setDate(LocalDate.of(2026, 10, 20));
        durgaPuja.setName("Durga Puja");
        when(holidayRepository.findByDateBetweenOrderByDateAsc(any(), any())).thenReturn(List.of(durgaPuja));

        assertThat(calculator.countWorkingDays(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 22))).isEqualTo(4);
    }

    @Test
    void weekendDaysAreConfigurable() {
        noHolidays();
        WorkingDayCalculator saturdaySunday = new WorkingDayCalculator(
                new LeaveProperties(Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)), holidayRepository);

        // Thursday..Sunday with a Sat/Sun weekend = Thursday + Friday.
        assertThat(saturdaySunday.countWorkingDays(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 27))).isEqualTo(2);
        assertThat(saturdaySunday.isWeekend(LocalDate.of(2026, 9, 25))).isFalse();
        assertThat(calculator.isWeekend(LocalDate.of(2026, 9, 25))).isTrue();
    }

    private void noHolidays() {
        when(holidayRepository.findByDateBetweenOrderByDateAsc(any(), any())).thenReturn(List.of());
    }
}
