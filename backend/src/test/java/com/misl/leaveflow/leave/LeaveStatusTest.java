package com.misl.leaveflow.leave;

import org.junit.jupiter.api.Test;

import static com.misl.leaveflow.leave.LeaveStatus.APPROVED;
import static com.misl.leaveflow.leave.LeaveStatus.CANCELLED;
import static com.misl.leaveflow.leave.LeaveStatus.PENDING;
import static com.misl.leaveflow.leave.LeaveStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;

class LeaveStatusTest {

    @Test
    void pending_canBeApprovedRejectedOrCancelled() {
        assertThat(PENDING.canTransitionTo(APPROVED)).isTrue();
        assertThat(PENDING.canTransitionTo(REJECTED)).isTrue();
        assertThat(PENDING.canTransitionTo(CANCELLED)).isTrue();
        assertThat(PENDING.canTransitionTo(PENDING)).isFalse();
    }

    @Test
    void approved_canOnlyBeCancelled() {
        assertThat(APPROVED.canTransitionTo(CANCELLED)).isTrue();
        assertThat(APPROVED.canTransitionTo(REJECTED)).isFalse();
        assertThat(APPROVED.canTransitionTo(PENDING)).isFalse();
        assertThat(APPROVED.canTransitionTo(APPROVED)).isFalse();
    }

    @Test
    void rejectedAndCancelled_areFinal() {
        for (LeaveStatus target : LeaveStatus.values()) {
            assertThat(REJECTED.canTransitionTo(target)).isFalse();
            assertThat(CANCELLED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    void onlyPendingAndApprovedHoldDays() {
        assertThat(LeaveStatus.activeStatuses()).containsExactlyInAnyOrder(PENDING, APPROVED);
    }
}
