package com.misl.leaveflow.employee;

/**
 * Access level of an employee.
 * ADMIN manages the organisation, MANAGER approves leave for direct reports,
 * EMPLOYEE manages their own leave only.
 */
public enum Role {
    ADMIN,
    MANAGER,
    EMPLOYEE
}
