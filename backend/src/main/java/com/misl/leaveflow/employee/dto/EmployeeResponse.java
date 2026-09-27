package com.misl.leaveflow.employee.dto;

import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.Role;

import java.time.Instant;
import java.time.LocalDate;

public record EmployeeResponse(
        Long id,
        String employeeCode,
        String fullName,
        String email,
        Role role,
        String department,
        String designation,
        Long managerId,
        String managerName,
        LocalDate joiningDate,
        boolean active,
        Instant createdAt
) {

    public static EmployeeResponse from(Employee employee) {
        Employee manager = employee.getManager();
        return new EmployeeResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                employee.getEmail(),
                employee.getRole(),
                employee.getDepartment(),
                employee.getDesignation(),
                manager != null ? manager.getId() : null,
                manager != null ? manager.getFullName() : null,
                employee.getJoiningDate(),
                employee.isActive(),
                employee.getCreatedAt()
        );
    }
}
