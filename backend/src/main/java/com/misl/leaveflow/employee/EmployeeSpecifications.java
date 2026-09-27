package com.misl.leaveflow.employee;

import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/**
 * Reusable WHERE-clause pieces for the employee search.
 * The service adds only the filters the client actually sent, so there is no "(:param is null or ...)" SQL.
 */
public final class EmployeeSpecifications {

    private EmployeeSpecifications() {
    }

    /** Case-insensitive partial match on name, email or employee code. */
    public static Specification<Employee> matchesSearch(String search) {
        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("fullName")), pattern),
                cb.like(cb.lower(root.get("email")), pattern),
                cb.like(cb.lower(root.get("employeeCode")), pattern));
    }

    public static Specification<Employee> hasDepartment(String department) {
        String value = department.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.get("department")), value);
    }

    public static Specification<Employee> hasRole(Role role) {
        return (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    public static Specification<Employee> hasActive(boolean active) {
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }
}
