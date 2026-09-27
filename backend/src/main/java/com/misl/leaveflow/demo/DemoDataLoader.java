package com.misl.leaveflow.demo;

import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.EmployeeRepository;
import com.misl.leaveflow.leave.LeaveRequest;
import com.misl.leaveflow.leave.LeaveRequestHistory;
import com.misl.leaveflow.leave.LeaveRequestHistoryRepository;
import com.misl.leaveflow.leave.LeaveRequestRepository;
import com.misl.leaveflow.leave.LeaveStatus;
import com.misl.leaveflow.leave.WorkingDayCalculator;
import com.misl.leaveflow.leave.type.LeaveType;
import com.misl.leaveflow.leave.type.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.misl.leaveflow.leave.LeaveStatus.APPROVED;
import static com.misl.leaveflow.leave.LeaveStatus.CANCELLED;
import static com.misl.leaveflow.leave.LeaveStatus.PENDING;
import static com.misl.leaveflow.leave.LeaveStatus.REJECTED;

/**
 * Fills an empty database with realistic leave history, so the dashboards, charts and calendar
 * have something to show in a demo.
 * <ul>
 *   <li>Dates are relative to <i>today</i>, so the demo always has someone on leave now, pending requests
 *       next week and approved leave earlier in the year. That is why this is Java and not a fixed-date SQL seed.</li>
 *   <li>It writes directly through the repositories (the "no past dates" rule is meant for users, not seed data),
 *       but uses the real {@link WorkingDayCalculator}, so working days are always correct.</li>
 *   <li>It runs only when there are no leave requests at all, and can be switched off with DEMO_DATA_ENABLED=false.</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataLoader implements ApplicationRunner {

    private static final String DOMAIN = "@leaveflow.com";

    /** startOffset is in days from today; length is in calendar days. */
    private record DemoLeave(String user, String type, int startOffset, int length, LeaveStatus status,
                             String reason, String comment) {
    }

    private static final List<DemoLeave> DEMO_LEAVES = List.of(
            // Earlier this year (approved history for the charts)
            new DemoLeave("rakib.hasan", "ANNUAL", -150, 5, APPROVED, "Family trip to Sylhet", "Approved, enjoy!"),
            new DemoLeave("sadia.islam", "SICK", -120, 2, APPROVED, "Fever", "Get well soon"),
            new DemoLeave("mahmud.hossain", "CASUAL", -95, 2, APPROVED, "Bank and passport work", null),
            new DemoLeave("nabila.chowdhury", "ANNUAL", -80, 5, APPROVED, "Sister's wedding", "Congratulations!"),
            new DemoLeave("arif.khan", "ANNUAL", -65, 5, APPROVED, "Hometown visit", null),
            new DemoLeave("tasnim.akter", "SICK", -50, 2, APPROVED, "Medical check-up", null),
            new DemoLeave("sadia.islam", "CASUAL", -45, 2, CANCELLED, "Personal errand", "No longer needed"),
            new DemoLeave("imran.kabir", "CASUAL", -40, 2, APPROVED, "Moving house", null),
            new DemoLeave("tanvir.ahmed", "ANNUAL", -30, 4, APPROVED, "Vacation", "Approved"),
            new DemoLeave("farhana.rahman", "CASUAL", -20, 2, APPROVED, "Family matter", null),
            new DemoLeave("mahmud.hossain", "UNPAID", -8, 3, APPROVED, "Extended personal leave", null),
            // Happening now
            new DemoLeave("nabila.chowdhury", "SICK", -1, 4, APPROVED, "Flu", "Rest well"),
            new DemoLeave("arif.khan", "ANNUAL", 0, 3, APPROVED, "Short break", null),
            // Coming up
            new DemoLeave("imran.kabir", "ANNUAL", 5, 4, APPROVED, "Travelling to Chattogram", "Approved"),
            new DemoLeave("rakib.hasan", "ANNUAL", 7, 5, PENDING, "Family event in Rajshahi", null),
            new DemoLeave("mahmud.hossain", "CASUAL", 10, 2, PENDING, "Personal work", null),
            new DemoLeave("tasnim.akter", "ANNUAL", 14, 5, PENDING, "Vacation", null),
            new DemoLeave("sadia.islam", "ANNUAL", 20, 4, REJECTED, "Short trip", "Release week, please pick other dates"),
            new DemoLeave("tanvir.ahmed", "SICK", 25, 2, PENDING, "Minor surgery", null)
    );

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveRequestHistoryRepository historyRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final WorkingDayCalculator workingDayCalculator;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (leaveRequestRepository.count() > 0) {
            log.info("Demo data: leave requests already exist, nothing to load");
            return;
        }
        LocalDate today = LocalDate.now(clock);
        Map<String, Employee> employees = employeeRepository.findAll().stream()
                .collect(Collectors.toMap(Employee::getEmail, Function.identity()));
        Map<String, LeaveType> types = leaveTypeRepository.findAll().stream()
                .collect(Collectors.toMap(LeaveType::getCode, Function.identity()));

        int created = 0;
        for (DemoLeave demo : DEMO_LEAVES) {
            Employee employee = employees.get(demo.user() + DOMAIN);
            LeaveType type = types.get(demo.type());
            if (employee != null && employee.isActive() && type != null && create(demo, employee, type, today)) {
                created++;
            }
        }
        log.info("Demo data: created {} leave requests", created);
    }

    private boolean create(DemoLeave demo, Employee employee, LeaveType type, LocalDate today) {
        LocalDate start = today.plusDays(demo.startOffset());
        LocalDate end = start.plusDays(demo.length() - 1L);
        int workingDays = workingDayCalculator.countWorkingDays(start, end);
        while (workingDays == 0) {
            // The range fell entirely on a weekend/holiday: stretch it until it contains a working day.
            end = end.plusDays(1);
            workingDays = workingDayCalculator.countWorkingDays(start, end);
        }
        if (start.getYear() != end.getYear()) {
            return false;
        }

        LeaveRequest leave = new LeaveRequest();
        leave.setEmployee(employee);
        leave.setLeaveType(type);
        leave.setStartDate(start);
        leave.setEndDate(end);
        leave.setWorkingDays(workingDays);
        leave.setReason(demo.reason());
        leave.setStatus(demo.status());

        Employee reviewer = employee.getManager();
        boolean reviewed = demo.status() == APPROVED || demo.status() == REJECTED;
        if (reviewed) {
            leave.setReviewedBy(reviewer);
            leave.setReviewComment(demo.comment());
            leave.setReviewedAt(Instant.now(clock));
        }
        leaveRequestRepository.save(leave);

        historyRepository.save(new LeaveRequestHistory(leave, null, PENDING, employee, "Request submitted"));
        if (demo.status() != PENDING) {
            Employee changedBy = reviewed ? reviewer : employee;
            historyRepository.save(new LeaveRequestHistory(leave, PENDING, demo.status(), changedBy, demo.comment()));
        }
        return true;
    }
}
