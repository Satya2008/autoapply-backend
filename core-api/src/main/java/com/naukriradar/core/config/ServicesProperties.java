package com.naukriradar.core.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("naukriradar.services")
public record ServicesProperties(
		@DefaultValue("http://localhost:8083") String matchingServiceUrl,
		@DefaultValue("http://localhost:8084") String applyWorkerUrl,
		@DefaultValue("http://localhost:8082") String jobServiceUrl,
		@DefaultValue("3s") Duration connectTimeout,
		@DefaultValue("15s") Duration readTimeout) {
}
