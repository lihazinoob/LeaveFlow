package com.misl.leaveflow.support;

import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.leave.LeaveRequest;
import com.misl.leaveflow.leave.LeaveStatus;
import com.misl.leaveflow.leave.type.LeaveType;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

/** Builds leave types and requests for tests without needing a database. */
public final class TestLeaves {

    private TestLeaves() {
    }

    public static LeaveType annualLeave() {
        return leaveType(1L, "ANNUAL", "Annual Leave", 15);
    }

    public static LeaveType unpaidLeave() {
        return leaveType(4L, "UNPAID", "Unpaid Leave", null);
    }

    public static LeaveType leaveType(long id, String code, String name, Integer quota) {
        LeaveType type = new LeaveType();
        type.setId(id);
        type.setCode(code);
        type.setName(name);
        type.setAnnualQuota(quota);
        type.setColor("#2563eb");
        return type;
    }

    public static LeaveRequest request(long id, Employee employee, LeaveStatus status, LocalDate start, LocalDate end) {
        LeaveRequest request = new LeaveRequest();
        ReflectionTestUtils.setField(request, "id", id);
        request.setEmployee(employee);
        request.setLeaveType(annualLeave());
        request.setStartDate(start);
        request.setEndDate(end);
        request.setWorkingDays(2);
        request.setReason("Family event");
        request.setStatus(status);
        return request;
    }
}
