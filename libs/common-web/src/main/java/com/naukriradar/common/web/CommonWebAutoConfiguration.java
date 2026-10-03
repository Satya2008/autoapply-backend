package com.naukriradar.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Registers the shared web beans in any servlet service that has this library on its
 * classpath, without the service having to scan this package.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonWebAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	GlobalExceptionHandler globalExceptionHandler() {
		return new GlobalExceptionHandler();
	}

	@Bean
	@ConditionalOnMissingBean
	CurrentUserProvider currentUserProvider(HttpServletRequest request) {
		return new HeaderCurrentUserProvider(request);
	}

}
