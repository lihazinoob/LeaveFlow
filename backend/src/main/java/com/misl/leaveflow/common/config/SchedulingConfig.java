package com.misl.leaveflow.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled background jobs (e.g. the nightly refresh-token cleanup). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
