package com.misl.leaveflow.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything the month grid needs in one call.
 *
 * @param month     e.g. "2026-10"
 * @param days      every day of the month, with weekend/holiday markers for shading
 * @param absences  approved and pending leave overlapping the month
 */
public record CalendarResponse(String month, String scope, List<Day> days, List<AbsenceItem> absences) {

    public record Day(LocalDate date, boolean weekend, String holidayName) {
    }
}
