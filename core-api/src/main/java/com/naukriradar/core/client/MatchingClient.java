package com.naukriradar.core.client;

import java.util.List;

import com.naukriradar.common.resilience.DependencyUnavailableException;
import com.naukriradar.common.resilience.Resilience;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
