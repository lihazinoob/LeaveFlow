package com.misl.leaveflow.support;

import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.Role;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

/** Builds Employee objects for tests without needing a database. */
public final class TestEmployees {

    private TestEmployees() {
    }

    public static Employee employee(long id, String email, Role role) {
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "id", id);
        employee.setEmail(email);
        employee.setFullName("Test " + role.name().toLowerCase());
        employee.setPasswordHash("$2a$10$hash");
        employee.setRole(role);
        employee.setDepartment("Engineering");
        employee.setDesignation("Engineer");
        employee.setJoiningDate(LocalDate.of(2024, 1, 1));
        return employee;
    }
}
