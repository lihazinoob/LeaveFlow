package com.misl.leaveflow.leave;

import com.misl.leaveflow.holiday.PublicHoliday;
import com.misl.leaveflow.holiday.PublicHolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Counts how many working days a leave actually costs.
 * A day counts only if it is neither a weekend day (Friday/Saturday by default) nor a public holiday.
 * Example: Thursday to Sunday = 2 working days (Thursday and Sunday).
 */
@Component
@EnableConfigurationProperties(LeaveProperties.class)
@RequiredArgsConstructor
public class WorkingDayCalculator {

    private final LeaveProperties leaveProperties;
    private final PublicHolidayRepository holidayRepository;

    /** Inclusive of both dates. Assumes {@code start <= end}. */
    public int countWorkingDays(LocalDate start, LocalDate end) {
        // One query for all holidays in the range, then an in-memory lookup for each day.
        Set<LocalDate> holidays = holidayRepository.findByDateBetweenOrderByDateAsc(start, end).stream()
                .map(PublicHoliday::getDate)
                .collect(Collectors.toSet());

        int workingDays = 0;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            if (isWorkingDay(day, holidays)) {
                workingDays++;
            }
        }
        return workingDays;
    }

    public boolean isWeekend(LocalDate day) {
        return leaveProperties.weekendDays().contains(day.getDayOfWeek());
    }

    private boolean isWorkingDay(LocalDate day, Set<LocalDate> holidays) {
        return !isWeekend(day) && !holidays.contains(day);
    }
}
