package com.misl.leaveflow.holiday.dto;

import com.misl.leaveflow.holiday.PublicHoliday;

import java.time.DayOfWeek;
import java.time.LocalDate;

public record HolidayResponse(Long id, LocalDate date, DayOfWeek dayOfWeek, String name) {

    public static HolidayResponse from(PublicHoliday holiday) {
        return new HolidayResponse(holiday.getId(), holiday.getDate(), holiday.getDate().getDayOfWeek(),
                holiday.getName());
    }
}
