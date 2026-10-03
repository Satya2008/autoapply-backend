package com.naukriradar.matching.config;

import java.util.List;

import com.naukriradar.common.security.CurrentUserProvider;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	private static final String USER_ID_SCHEME = "userId";

	@Bean
	OpenAPI matchingServiceOpenApi() {
		SecurityScheme userIdHeader = new SecurityScheme()
				.type(SecurityScheme.Type.APIKEY)
				.in(SecurityScheme.In.HEADER)
				.name(CurrentUserProvider.USER_ID_HEADER)
				.description("Dev only: the id returned by POST /api/v1/dev/users.");
		return new OpenAPI()
				.info(new Info().title("matching-service").version("v1")
						.description("Scores jobs against your profile and keeps the best matches."))
				.servers(List.of(new Server().url("/")))
				.components(new Components().addSecuritySchemes(USER_ID_SCHEME, userIdHeader))
				.addSecurityItem(new SecurityRequirement().addList(USER_ID_SCHEME));
	}

}
