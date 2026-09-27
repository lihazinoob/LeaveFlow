package com.misl.leaveflow.leave;

import com.misl.leaveflow.employee.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Audit trail: one immutable row per change of a leave request (who, when, from which status to which). */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "leave_request_history")
public class LeaveRequestHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_request_id", nullable = false)
    private LeaveRequest leaveRequest;

    /** Null for the first entry (the request was just submitted). */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private LeaveStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private LeaveStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by", nullable = false)
    private Employee changedBy;

    @Column(length = 500)
    private String comment;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    public LeaveRequestHistory(LeaveRequest leaveRequest, LeaveStatus fromStatus, LeaveStatus toStatus,
                               Employee changedBy, String comment) {
        this.leaveRequest = leaveRequest;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.comment = comment;
    }
}
