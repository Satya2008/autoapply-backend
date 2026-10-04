package com.naukriradar.common.resilience;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(ResilienceProperties.class)
public class ResilienceAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	Resilience resilience(ResilienceProperties properties) {
		return new Resilience(properties);
	}

	@Bean
	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
	ResilienceAdminController resilienceAdminController(Resilience resilience) {
		return new ResilienceAdminController(resilience);
	}

}
