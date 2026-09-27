package com.misl.leaveflow.employee.dto;

import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.Role;

/** Lightweight item for dropdowns (e.g. choosing a manager). */
public record EmployeeOption(Long id, String employeeCode, String fullName, Role role, String department) {

    public static EmployeeOption from(Employee employee) {
        return new EmployeeOption(employee.getId(), employee.getEmployeeCode(), employee.getFullName(),
                employee.getRole(), employee.getDepartment());
    }
}
