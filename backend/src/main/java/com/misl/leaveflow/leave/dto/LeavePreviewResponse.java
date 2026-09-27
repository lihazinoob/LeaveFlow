package com.misl.leaveflow.leave.dto;

import java.util.List;

/**
 * What a request would cost, before it is submitted. Shown live in the apply form.
 *
 * @param workingDays   days that would be deducted (weekends and holidays excluded)
 * @param availableDays balance left for this leave type before this request; null = unlimited
 * @param valid         true when the request can be submitted as it is
 * @param problems      every rule the request currently breaks; empty when valid
 */
public record LeavePreviewResponse(int workingDays, Integer availableDays, boolean valid, List<String> problems) {

    public static LeavePreviewResponse of(int workingDays, Integer availableDays, List<String> problems) {
        return new LeavePreviewResponse(workingDays, availableDays, problems.isEmpty(), problems);
    }
}
