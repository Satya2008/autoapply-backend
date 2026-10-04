package com.naukriradar.job.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import com.naukriradar.common.resilience.DependencyUnavailableException;
import com.naukriradar.common.resilience.Resilience;
import com.naukriradar.job.config.AiParsingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Asks matching-service's AI for an answer. Empty when AI isn't available (off, no provider,
 * all down): job-service works the same without it.
 */
@Component
public class AiGatewayClient {

	private static final Logger log = LoggerFactory.getLogger(AiGatewayClient.class);

	private final RestClient client;
	private final Resilience resilience;

	public AiGatewayClient(RestClient.Builder builder, AiParsingProperties properties, Resilience resilience) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build());
		factory.setReadTimeout(properties.readTimeout());
		this.client = builder.clone().requestFactory(factory).baseUrl(properties.matchingServiceUrl()).build();
		this.resilience = resilience;
	}

	/** @return the checked answer, or empty when AI couldn't answer */
	public Optional<JsonNode> run(String prompt, Map<String, String> variables) {
		try {
			JsonNode reply = resilience.call("matching-service", () -> client.post()
					.uri("/internal/v1/ai/run")
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("prompt", prompt, "variables", variables))
					.retrieve()
					.body(JsonNode.class));
			JsonNode answer = reply == null ? null : reply.get("answer");
			if (answer == null || answer.isNull()) {
				log.info("No AI answer for {}: {}", prompt, reply == null ? "empty reply" : reply.path("unavailableReason").asString());
				return Optional.empty();
			}
			return Optional.of(answer);
		}
		catch (RestClientException | DependencyUnavailableException ex) {
			log.info("AI not available for {}: {}", prompt, ex.getMessage());
			return Optional.empty();
		}
	}

}
