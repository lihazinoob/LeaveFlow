package com.misl.leaveflow.leave.dto;

import com.misl.leaveflow.leave.LeaveRequestHistory;
import com.misl.leaveflow.leave.LeaveStatus;

import java.time.Instant;

public record LeaveHistoryResponse(
        Long id,
        LeaveStatus fromStatus,
        LeaveStatus toStatus,
        Long changedById,
        String changedByName,
        String comment,
        Instant changedAt
) {

    public static LeaveHistoryResponse from(LeaveRequestHistory entry) {
        return new LeaveHistoryResponse(entry.getId(), entry.getFromStatus(), entry.getToStatus(),
                entry.getChangedBy().getId(), entry.getChangedBy().getFullName(), entry.getComment(),
                entry.getChangedAt());
    }
}
