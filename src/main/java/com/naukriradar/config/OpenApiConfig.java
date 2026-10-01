package com.naukriradar.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI metadata, plus an Authorize box for the dev {@code X-User-Id} header. */
@Configuration
class OpenApiConfig {

	private static final String USER_ID_SCHEME = "userId";

	@Bean
	OpenAPI naukriRadarOpenApi() {
		SecurityScheme userIdHeader = new SecurityScheme()
				.type(SecurityScheme.Type.APIKEY)
				.in(SecurityScheme.In.HEADER)
				.name(HeaderCurrentUserProvider.HEADER)
				.description("Dev only: the id returned by POST /api/v1/dev/users.");
		return new OpenAPI()
				.info(new Info().title("NaukriRadar API").version("v1"))
				.components(new Components().addSecuritySchemes(USER_ID_SCHEME, userIdHeader))
				.addSecurityItem(new SecurityRequirement().addList(USER_ID_SCHEME));
	}

}
