package com.naukriradar.core.config;

import java.util.List;

import com.naukriradar.common.web.CurrentUserProvider;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger metadata, plus an Authorize box for the {@code X-User-Id} header. */
@Configuration
class OpenApiConfig {

	private static final String USER_ID_SCHEME = "userId";

	@Bean
	OpenAPI coreApiOpenApi() {
		SecurityScheme userIdHeader = new SecurityScheme()
				.type(SecurityScheme.Type.APIKEY)
				.in(SecurityScheme.In.HEADER)
				.name(CurrentUserProvider.USER_ID_HEADER)
				.description("Dev only: the id returned by POST /api/v1/dev/users.");
		return new OpenAPI()
				.info(new Info().title("core-api").version("v1")
						.description("Users, profiles and skills."))
				// Relative, so "Try it out" calls whichever host served the docs: the gateway
				// when viewed there, this service when viewed directly.
				.servers(List.of(new Server().url("/")))
				.components(new Components().addSecuritySchemes(USER_ID_SCHEME, userIdHeader))
				.addSecurityItem(new SecurityRequirement().addList(USER_ID_SCHEME));
	}

}
