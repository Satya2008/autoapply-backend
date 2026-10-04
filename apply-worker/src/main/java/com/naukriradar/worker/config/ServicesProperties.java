package com.naukriradar.worker.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("naukriradar.services")
public record ServicesProperties(
		@DefaultValue("http://localhost:8081") String coreApiUrl,
		@DefaultValue("3s") Duration connectTimeout,
		@DefaultValue("10s") Duration readTimeout) {
}
