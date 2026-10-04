package com.naukriradar.matching.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.naukriradar.matching.config.AiProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** What the vendor clients share: one RestClient per account, and the JSON instructions. */
abstract class HttpAiClient implements AiClient {

	private final RestClient.Builder builder;
	private final Map<String, RestClient> clients = new ConcurrentHashMap<>();
	protected final JsonMapper json;

	HttpAiClient(RestClient.Builder builder, JsonMapper json) {
		this.builder = builder;
		this.json = json;
	}

	protected RestClient client(AiProperties.Provider provider) {
		return clients.computeIfAbsent(provider.baseUrl() + "|" + provider.timeout(), key -> {
			// HTTP/1.1: on plain http the JDK client would try an h2c upgrade, which some servers drop
			HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
					.connectTimeout(Duration.ofSeconds(5)).build();
			JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
			factory.setReadTimeout(provider.timeout());
			return builder.clone().requestFactory(factory).baseUrl(provider.baseUrl()).build();
		});
	}

	/** The system prompt, plus the JSON contract when the request has one. */
	protected String system(AiRequest request) {
		String system = request.system() == null ? "" : request.system();
		if (request.outputSchema() == null) {
			return system;
		}
		return (system.isBlank() ? "" : system + "\n\n")
				+ "Reply with one JSON object only, no prose and no code fences, matching this JSON Schema:\n"
				+ json.writeValueAsString(request.outputSchema());
	}

	protected static String text(JsonNode node, String what) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			throw new AiProviderException("The reply has no " + what + ".");
		}
		return node.asString();
	}

}
