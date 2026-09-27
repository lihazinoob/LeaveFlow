package com.misl.leaveflow.leave.type;

public record LeaveTypeResponse(Long id, String code, String name, String description, Integer annualQuota,
                                String color) {

    public static LeaveTypeResponse from(LeaveType type) {
        return new LeaveTypeResponse(type.getId(), type.getCode(), type.getName(), type.getDescription(),
                type.getAnnualQuota(), type.getColor());
    }
}
