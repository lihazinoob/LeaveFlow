package com.misl.leaveflow.employee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /** Emails are stored in lower case, so callers must normalise before looking up. */
    Optional<Employee> findByEmail(String email);
}
