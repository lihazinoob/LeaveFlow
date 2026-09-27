package com.misl.leaveflow.employee;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    /** Emails are stored in lower case, so callers must normalise before looking up. */
    Optional<Employee> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    /** True if someone still active reports to this employee. */
    boolean existsByManagerIdAndActiveTrue(Long managerId);

    List<Employee> findByManagerIdAndActiveTrueOrderByFullNameAsc(Long managerId);

    List<Employee> findByRoleInAndActiveTrueOrderByFullNameAsc(Collection<Role> roles);

    @Query("select distinct e.department from Employee e order by e.department")
    List<String> findDistinctDepartments();

    /** Filtered, paged search. The manager is fetched in the same query to avoid one extra query per row. */
    @Override
    @EntityGraph(attributePaths = "manager")
    Page<Employee> findAll(Specification<Employee> specification, Pageable pageable);
}
