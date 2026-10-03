package com.naukriradar.common.config;

import com.naukriradar.common.exception.GlobalExceptionHandler;
import com.naukriradar.common.security.CurrentUserProvider;
import com.naukriradar.common.security.HeaderCurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Registers the shared web beans in every servlet service that has this library on its
 * classpath, so services don't need to component-scan the library.
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
