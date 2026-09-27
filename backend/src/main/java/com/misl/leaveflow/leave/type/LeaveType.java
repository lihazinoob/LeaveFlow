package com.misl.leaveflow.leave.type;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A kind of leave (Annual, Sick, ...) with its yearly allowance. Seeded by Flyway (V4). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "leave_types")
public class LeaveType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    /** Working days allowed per calendar year; null means unlimited (e.g. unpaid leave). */
    @Column(name = "annual_quota")
    private Integer annualQuota;

    @Column(nullable = false, length = 7)
    private String color;

    public boolean hasQuota() {
        return annualQuota != null;
    }
}
