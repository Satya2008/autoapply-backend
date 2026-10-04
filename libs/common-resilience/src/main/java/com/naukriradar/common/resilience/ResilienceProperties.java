package com.naukriradar.common.resilience;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Defaults for every dependency.
 *
 * @param maxAttempts calls in total, the first one included
 * @param firstBackoff wait before the second attempt; it doubles each time
 * @param jitter how far a wait may randomly move (0.5 = up to 50% either way), so clients that
 *     failed together don't all retry at the same instant
 * @param failureRate percent of failed calls in the window that opens the circuit
 * @param window how many recent calls the failure rate is measured over
 * @param minimumCalls calls needed before the rate means anything
 * @param openFor how long an open circuit refuses calls before letting a few through to test
 * @param maxConcurrent calls to one dependency in flight at once; more are refused at once
 */
@ConfigurationProperties("naukriradar.resilience")
public record ResilienceProperties(
		@DefaultValue("3") int maxAttempts,
		@DefaultValue("200ms") Duration firstBackoff,
		@DefaultValue("0.5") double jitter,
		@DefaultValue("50") float failureRate,
		@DefaultValue("20") int window,
		@DefaultValue("10") int minimumCalls,
		@DefaultValue("30s") Duration openFor,
		@DefaultValue("10") int maxConcurrent) {
}
