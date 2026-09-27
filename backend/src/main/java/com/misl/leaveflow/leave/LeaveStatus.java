package com.misl.leaveflow.leave;

import java.util.Set;

/**
 * Lifecycle of a leave request (a small state machine):
 * <pre>
 *            approve
 *  PENDING ----------> APPROVED
 *     |  \ reject          |
 *     |   `-------> REJECTED (final)
 *     | cancel             | cancel (only before the leave starts)
 *     v                    v
 *  CANCELLED (final) <-----'
 * </pre>
 * Who may make a transition (owner, manager, admin) is checked in the service.
 */
public enum LeaveStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED;

    public boolean canTransitionTo(LeaveStatus target) {
        return switch (this) {
            case PENDING -> Set.of(APPROVED, REJECTED, CANCELLED).contains(target);
            case APPROVED -> target == CANCELLED;
            case REJECTED, CANCELLED -> false;
        };
    }

    /** Statuses that "hold" days: they block overlapping requests and count against the balance. */
    public static Set<LeaveStatus> activeStatuses() {
        return Set.of(PENDING, APPROVED);
    }
}
