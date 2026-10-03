package com.naukriradar.job.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * @param allowPrivateHosts let sources point at localhost or private networks; off in
 * production so an admin-entered URL can't be used to probe internal services
 * @param maxResponseSize larger board responses are refused rather than loaded into memory
 * @param connectTimeout per connection; the read timeout comes from each source
 * @param disableAfterFailures consecutive failed runs before a source is switched off (0 = never)
 * @param userAgent sent on every board request
 * @param seedDefaults create the sources in {@code sources/default-sources.json} on startup
 * if they don't exist yet
 */
@ConfigurationProperties("naukriradar.jobs")
public record JobsProperties(
		@DefaultValue("false") boolean allowPrivateHosts,
		@DefaultValue("5MB") DataSize maxResponseSize,
		@DefaultValue("5s") Duration connectTimeout,
		@DefaultValue("5") int disableAfterFailures,
		@DefaultValue("NaukriRadar/1.0 (+job aggregation)") String userAgent,
		@DefaultValue("true") boolean seedDefaults) {
}
