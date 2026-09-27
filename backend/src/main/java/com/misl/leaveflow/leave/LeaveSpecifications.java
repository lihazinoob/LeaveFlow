package com.misl.leaveflow.leave;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Locale;

/** Reusable WHERE-clause pieces for leave request lists; the service combines only the ones it needs. */
public final class LeaveSpecifications {

    private LeaveSpecifications() {
    }

    public static Specification<LeaveRequest> ofEmployee(Long employeeId) {
        return (root, query, cb) -> cb.equal(root.get("employee").get("id"), employeeId);
    }

    public static Specification<LeaveRequest> notOfEmployee(Long employeeId) {
        return (root, query, cb) -> cb.notEqual(root.get("employee").get("id"), employeeId);
    }

    /** Requests of the people who report directly to this manager. */
    public static Specification<LeaveRequest> ofTeamManagedBy(Long managerId) {
        return (root, query, cb) -> cb.equal(root.get("employee").get("manager").get("id"), managerId);
    }

    public static Specification<LeaveRequest> ofActiveEmployees() {
        return (root, query, cb) -> cb.isTrue(root.get("employee").get("active"));
    }

    public static Specification<LeaveRequest> hasStatus(LeaveStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<LeaveRequest> hasLeaveType(Long leaveTypeId) {
        return (root, query, cb) -> cb.equal(root.get("leaveType").get("id"), leaveTypeId);
    }

    /** Leave that touches the range [from, to] in any way (starts, ends or runs through it). */
    public static Specification<LeaveRequest> overlaps(LocalDate from, LocalDate to) {
        return (root, query, cb) -> cb.and(
                cb.lessThanOrEqualTo(root.get("startDate"), to),
                cb.greaterThanOrEqualTo(root.get("endDate"), from));
    }

    /** Used with {@link #startsOnOrBefore} for an open-ended date filter ("from" and/or "to"). */
    public static Specification<LeaveRequest> endsOnOrAfter(LocalDate from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("endDate"), from);
    }

    public static Specification<LeaveRequest> startsOnOrBefore(LocalDate to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("startDate"), to);
    }

    /** Case-insensitive match on the employee's name or code. */
    public static Specification<LeaveRequest> employeeMatches(String search) {
        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("employee").get("fullName")), pattern),
                cb.like(cb.lower(root.get("employee").get("employeeCode")), pattern));
    }
}
