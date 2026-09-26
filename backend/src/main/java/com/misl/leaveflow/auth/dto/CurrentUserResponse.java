package com.misl.leaveflow.auth.dto;

import com.misl.leaveflow.employee.Employee;
import com.misl.leaveflow.employee.Role;

import java.time.LocalDate;

/** Profile of the logged-in user, used by the frontend to render the right menus and dashboard. */
public record CurrentUserResponse(
        Long id,
        String employeeCode,
        String fullName,
        String email,
        Role role,
        String department,
        String designation,
        LocalDate joiningDate,
        String managerName
) {

    public static CurrentUserResponse from(Employee employee) {
        Employee manager = employee.getManager();
        return new CurrentUserResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                employee.getEmail(),
                employee.getRole(),
                employee.getDepartment(),
                employee.getDesignation(),
                employee.getJoiningDate(),
                manager != null ? manager.getFullName() : null
        );
    }
}
