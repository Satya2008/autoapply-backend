package com.naukriradar.core.client;

import java.util.ArrayList;
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

	/** matching-service takes queries up to this long. */
	private static final int MAX_QUERY = 4000;

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

	/**
	 * The ids of the passages that best answer the query, best first (hybrid: meaning and
	 * keywords). Empty when matching-service can't be reached: the caller falls back to its
	 * own order rather than failing.
	 */
	public List<String> retrieve(String query, Map<String, String> passagesById, int top) {
		if (passagesById.isEmpty() || query == null || query.isBlank()) {
			return List.of();
		}
		List<Map<String, String>> passages = passagesById.entrySet().stream()
				.map(e -> Map.of("id", e.getKey(), "text", e.getValue()))
				.toList();
		String clipped = query.length() > MAX_QUERY ? query.substring(0, MAX_QUERY) : query;
		try {
			JsonNode reply = resilience.call("matching-service", () -> matchingRestClient.post()
					.uri("/internal/v1/ai/retrieve")
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("query", clipped, "passages", passages, "top", top))
					.retrieve()
					.body(JsonNode.class));
			List<String> ids = new ArrayList<>();
			if (reply != null) {
				reply.path("passages").forEach(p -> ids.add(p.path("id").asString()));
			}
			return ids;
		}
		catch (RestClientException | DependencyUnavailableException ex) {
			return List.of();
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
