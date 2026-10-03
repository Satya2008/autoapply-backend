package com.naukriradar.matching.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Where the other services are. Until Kafka (Phase 14) they are called over REST. */
@ConfigurationProperties("naukriradar.services")
public record ServicesProperties(
		@DefaultValue("http://localhost:8081") String coreApiUrl,
		@DefaultValue("http://localhost:8082") String jobServiceUrl,
		@DefaultValue("3s") Duration connectTimeout,
		@DefaultValue("15s") Duration readTimeout) {
}
