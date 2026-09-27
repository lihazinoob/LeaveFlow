package com.misl.leaveflow.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * The single source of "now" for business rules such as "a leave cannot start in the past".
 * <ul>
 *   <li>It uses the company's time zone (Asia/Dhaka), not the server's. A container usually runs in UTC,
 *       where "today" changes 6 hours later than in Dhaka.</li>
 *   <li>Tests can replace it with a fixed clock.</li>
 * </ul>
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.time-zone:Asia/Dhaka}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
