package com.naukriradar.core.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import com.naukriradar.common.resilience.DependencyUnavailableException;
import com.naukriradar.common.resilience.Resilience;
import com.naukriradar.core.config.ServicesProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/** The apply worker's dry run: fill a portal's form with sample answers, submit nothing. */
@Component
public class ApplyWorkerClient {

	private final RestClient client;
	private final Resilience resilience;

	public ApplyWorkerClient(RestClient.Builder builder, ServicesProperties properties, Resilience resilience) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1).connectTimeout(properties.connectTimeout()).build());
		// a browser opening a page takes a while
		factory.setReadTimeout(Duration.ofSeconds(90));
		this.client = builder.clone().requestFactory(factory).baseUrl(properties.applyWorkerUrl()).build();
		this.resilience = resilience;
	}

	public JsonNode dryRun(String url, Map<String, String> selectors) {
		try {
			return resilience.call("apply-worker", () -> client.post()
					.uri("/internal/v1/dry-run")
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("url", url, "selectors", selectors))
					.retrieve()
					.body(JsonNode.class));
		}
		catch (RestClientException | DependencyUnavailableException ex) {
			throw new UpstreamException("The apply worker is unavailable right now. Try again shortly.", ex);
		}
	}

}
