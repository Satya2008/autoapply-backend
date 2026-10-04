package com.naukriradar.common.events;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param relayEvery how often unsent outbox rows are looked for
 * @param retries attempts after the first before an event goes to the dead letters
 * @param retryBackoff wait between those attempts
 */
@ConfigurationProperties("naukriradar.events")
public record EventsProperties(
		@DefaultValue("500ms") Duration relayEvery,
		@DefaultValue("2") int retries,
		@DefaultValue("1s") Duration retryBackoff) {
}
