package com.misl.leaveflow.leave;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.DayOfWeek;
import java.util.Set;

/**
 * Leave settings bound from {@code app.leave.*}.
 *
 * @param weekendDays days that are never working days. Bangladesh uses FRIDAY and SATURDAY.
 */
@ConfigurationProperties(prefix = "app.leave")
public record LeaveProperties(Set<DayOfWeek> weekendDays) {
}
