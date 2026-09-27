package com.misl.leaveflow.leave;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Leave request queries. The @EntityGraph annotations load the employee (and their manager), leave type
 * and reviewer in the same SQL query, because every response needs them.
 */
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long>,
        JpaSpecificationExecutor<LeaveRequest> {

    @EntityGraph(attributePaths = {"employee", "employee.manager", "leaveType", "reviewedBy"})
    Optional<LeaveRequest> findWithDetailsById(Long id);

    @Override
    @EntityGraph(attributePaths = {"employee", "employee.manager", "leaveType", "reviewedBy"})
    Page<LeaveRequest> findAll(Specification<LeaveRequest> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"employee", "employee.manager", "leaveType", "reviewedBy"})
    List<LeaveRequest> findAll(Specification<LeaveRequest> specification, Sort sort);

    /**
     * Does the employee already have a pending/approved request overlapping [start, end]?
     * Two ranges overlap when each one starts before (or on the day) the other ends.
     * {@code excludeId} skips the request being edited (use -1 when creating).
     */
    @Query("""
            select count(r) > 0 from LeaveRequest r
            where r.employee.id = :employeeId
              and r.status in :statuses
              and r.id <> :excludeId
              and r.startDate <= :end and r.endDate >= :start
            """)
    boolean existsOverlapping(@Param("employeeId") Long employeeId,
                              @Param("start") LocalDate start,
                              @Param("end") LocalDate end,
                              @Param("statuses") Collection<LeaveStatus> statuses,
                              @Param("excludeId") Long excludeId);

    /** Working days per leave type and status for one employee in a date range (used for balances). */
    @Query("""
            select new com.misl.leaveflow.leave.LeaveUsage(r.leaveType.id, r.status, sum(r.workingDays))
            from LeaveRequest r
            where r.employee.id = :employeeId
              and r.status in :statuses
              and r.startDate between :from and :to
              and r.id <> :excludeId
            group by r.leaveType.id, r.status
            """)
    List<LeaveUsage> sumWorkingDays(@Param("employeeId") Long employeeId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to,
                                    @Param("statuses") Collection<LeaveStatus> statuses,
                                    @Param("excludeId") Long excludeId);

    @EntityGraph(attributePaths = {"employee", "employee.manager", "leaveType", "reviewedBy"})
    List<LeaveRequest> findByEmployeeIdAndStatus(Long employeeId, LeaveStatus status);
}
