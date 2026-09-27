package com.misl.leaveflow.dashboard;

import com.misl.leaveflow.dashboard.dto.AbsenceItem;
import com.misl.leaveflow.dashboard.dto.CalendarResponse;
import com.misl.leaveflow.employee.Role;
import com.misl.leaveflow.holiday.PublicHoliday;
import com.misl.leaveflow.holiday.PublicHolidayRepository;
import com.misl.leaveflow.leave.LeaveRequest;
import com.misl.leaveflow.leave.LeaveStatus;
import com.misl.leaveflow.leave.WorkingDayCalculator;
import com.misl.leaveflow.security.EmployeeUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Team leave calendar for one month: managers see their direct reports, admins see everyone.
 * Pending leave is included (the UI draws it differently) so a manager can spot clashes before approving.
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

    private final LeaveStatsRepository statsRepository;
    private final PublicHolidayRepository holidayRepository;
    private final WorkingDayCalculator workingDayCalculator;

    @Transactional(readOnly = true)
    public CalendarResponse getMonth(EmployeeUserDetails user, YearMonth month) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        Set<LeaveStatus> statuses = Set.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);
        boolean admin = user.getRole() == Role.ADMIN;

        List<LeaveRequest> leaves = admin
                ? statsRepository.findInRange(first, last, statuses)
                : statsRepository.findInRangeForTeam(user.getId(), first, last, statuses);

        Map<LocalDate, String> holidayNames = holidayRepository.findByDateBetweenOrderByDateAsc(first, last).stream()
                .collect(Collectors.toMap(PublicHoliday::getDate, PublicHoliday::getName));

        List<CalendarResponse.Day> days = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            days.add(new CalendarResponse.Day(day, workingDayCalculator.isWeekend(day), holidayNames.get(day)));
        }

        return new CalendarResponse(month.toString(), admin ? "ORGANISATION" : "TEAM", days,
                leaves.stream().map(AbsenceItem::from).toList());
    }
}
