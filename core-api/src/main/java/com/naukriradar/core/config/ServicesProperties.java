package com.naukriradar.core.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("naukriradar.services")
public record ServicesProperties(
		@DefaultValue("http://localhost:8083") String matchingServiceUrl,
		@DefaultValue("3s") Duration connectTimeout,
		@DefaultValue("15s") Duration readTimeout) {
}
