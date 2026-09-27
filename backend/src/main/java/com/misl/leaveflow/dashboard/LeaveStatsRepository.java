package com.misl.leaveflow.dashboard;

import com.misl.leaveflow.dashboard.dto.LabelTotal;
import com.misl.leaveflow.leave.LeaveRequest;
import com.misl.leaveflow.leave.LeaveStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Read-only reporting queries used by the dashboards and the calendar.
 * Kept apart from LeaveRequestRepository so that the "reporting" side is easy to find.
 * Extends the bare {@link Repository} interface, so it offers no save/delete methods at all.
 */
public interface LeaveStatsRepository extends Repository<LeaveRequest, Long> {

    /** Approved working days per leave type for leaves starting in [from, to]. */
    @Query("""
            select new com.misl.leaveflow.dashboard.dto.LabelTotal(t.name, t.color, sum(r.workingDays))
            from LeaveRequest r join r.leaveType t
            where r.status = com.misl.leaveflow.leave.LeaveStatus.APPROVED
              and r.startDate between :from and :to
            group by t.id, t.name, t.color
            order by t.id
            """)
    List<LabelTotal> approvedDaysByType(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Approved working days per department (of the employee) for leaves starting in [from, to]. */
    @Query("""
            select new com.misl.leaveflow.dashboard.dto.LabelTotal(e.department, sum(r.workingDays))
            from LeaveRequest r join r.employee e
            where r.status = com.misl.leaveflow.leave.LeaveStatus.APPROVED
              and r.startDate between :from and :to
            group by e.department
            order by e.department
            """)
    List<LabelTotal> approvedDaysByDepartment(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Approved working days per month (1-12). A leave is counted in the month it starts;
     * this keeps the query simple and is accurate for the usual short leaves.
     */
    @Query("""
            select extract(month from r.startDate), sum(r.workingDays)
            from LeaveRequest r
            where r.status = com.misl.leaveflow.leave.LeaveStatus.APPROVED
              and r.startDate between :from and :to
            group by extract(month from r.startDate)
            """)
    List<Object[]> approvedDaysByMonth(@Param("from") LocalDate from, @Param("to") LocalDate to);

    long countByStatusAndEmployeeActiveTrue(LeaveStatus status);

    /** Active employees per department (queries the Employee entity; kept here with the other reports). */
    @Query("""
            select new com.misl.leaveflow.dashboard.dto.LabelTotal(e.department, count(e))
            from Employee e
            where e.active = true
            group by e.department
            order by e.department
            """)
    List<LabelTotal> activeHeadcountByDepartment();

    /**
     * Leave touching the range [from, to] with one of the given statuses, for active employees only.
     * Used for "who is out today / this week" and the calendar.
     */
    @EntityGraph(attributePaths = {"employee", "leaveType"})
    @Query("""
            select r from LeaveRequest r
            where r.status in :statuses
              and r.employee.active = true
              and r.startDate <= :to and r.endDate >= :from
            order by r.startDate, r.employee.fullName
            """)
    List<LeaveRequest> findInRange(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                   @Param("statuses") Collection<LeaveStatus> statuses);

    /** Same as {@link #findInRange} but limited to one manager's direct reports. */
    @EntityGraph(attributePaths = {"employee", "leaveType"})
    @Query("""
            select r from LeaveRequest r
            where r.status in :statuses
              and r.employee.active = true
              and r.employee.manager.id = :managerId
              and r.startDate <= :to and r.endDate >= :from
            order by r.startDate, r.employee.fullName
            """)
    List<LeaveRequest> findInRangeForTeam(@Param("managerId") Long managerId,
                                          @Param("from") LocalDate from, @Param("to") LocalDate to,
                                          @Param("statuses") Collection<LeaveStatus> statuses);

    /** The employee's own leave that has not ended yet (pending or approved), soonest first. */
    @EntityGraph(attributePaths = {"employee", "employee.manager", "leaveType", "reviewedBy"})
    @Query("""
            select r from LeaveRequest r
            where r.employee.id = :employeeId
              and r.status in :statuses
              and r.endDate >= :today
            order by r.startDate
            """)
    List<LeaveRequest> findUpcomingForEmployee(@Param("employeeId") Long employeeId,
                                               @Param("today") LocalDate today,
                                               @Param("statuses") Collection<LeaveStatus> statuses);
}
