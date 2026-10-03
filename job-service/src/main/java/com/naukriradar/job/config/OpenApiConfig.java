package com.naukriradar.job.config;

import java.util.List;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI jobServiceOpenApi() {
		return new OpenAPI()
				.info(new Info().title("job-service").version("v1")
						.description("Job boards as configuration, and the jobs fetched from them."))
				// relative so "Try it out" goes through whichever host served the docs
				.servers(List.of(new Server().url("/")));
	}

}
