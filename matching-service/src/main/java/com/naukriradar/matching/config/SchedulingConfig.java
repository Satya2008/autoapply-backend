package com.naukriradar.matching.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Scheduled jobs (the nightly batch); off with naukriradar.scheduling.enabled=false, as in tests. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "naukriradar.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
