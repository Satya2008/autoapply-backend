package com.naukriradar.core.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.naukriradar.common.resilience.DependencyUnavailableException;
import com.naukriradar.common.resilience.Resilience;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Component
public class MatchingClient {

	private static final ParameterizedTypeReference<List<MatchForApply>> MATCHES = new ParameterizedTypeReference<>() {
	};

	private final RestClient matchingRestClient;
	private final Resilience resilience;

	public MatchingClient(RestClient matchingRestClient, Resilience resilience) {
		this.matchingRestClient = matchingRestClient;
		this.resilience = resilience;
	}

	/**
	 * Asks matching-service's AI (which owns the providers, cache, budget and cost tracking).
	 *
	 * @param userId whose AI budget it counts against
	 * @param fresh skip the cache, for "write it again"
	 */
	public AiReply ai(String prompt, Map<String, String> variables, String userId, boolean fresh) {
		Map<String, Object> body = new HashMap<>();
		body.put("prompt", prompt);
		body.put("variables", variables);
		body.put("userId", userId);
		body.put("fresh", fresh);
		try {
			JsonNode reply = resilience.call("matching-service", () -> matchingRestClient.post()
					.uri("/internal/v1/ai/run")
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(JsonNode.class));
			if (reply == null) {
				return new AiReply(null, "empty reply", null);
			}
			JsonNode answer = reply.get("answer");
			if (answer == null || answer.isNull()) {
				return new AiReply(null, reply.path("unavailableReason").asString(), null);
			}
			return new AiReply(answer, null, reply.path("provider").asString() + ":" + reply.path("model").asString());
		}
		catch (RestClientException | DependencyUnavailableException ex) {
			return new AiReply(null, "matching-service is unavailable right now.", null);
		}
	}

	/** The user's matches, best first. */
	public List<MatchForApply> matches(String userId, int limit) {
		try {
			List<MatchForApply> matches = resilience.call("matching-service", () -> matchingRestClient.get()
					.uri(uri -> uri.path("/internal/v1/users/{userId}/matches").queryParam("limit", limit).build(userId))
					.retrieve()
					.body(MATCHES));
			return matches == null ? List.of() : matches;
		}
		catch (RestClientException | DependencyUnavailableException ex) {
			throw new UpstreamException("matching-service is unavailable right now. Try again shortly.", ex);
		}
	}

}
