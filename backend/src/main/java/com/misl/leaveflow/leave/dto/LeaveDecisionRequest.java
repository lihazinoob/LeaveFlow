package com.misl.leaveflow.leave.dto;

import jakarta.validation.constraints.Size;

/** Optional comment for approve/cancel. Reject requires one (checked in the service). */
public record LeaveDecisionRequest(
        @Size(max = 500, message = "Comment must be at most 500 characters")
        String comment
) {
}
