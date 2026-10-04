package com.naukriradar.matching.client;

import com.naukriradar.common.resilience.DependencyUnavailableException;
import com.naukriradar.common.resilience.Resilience;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class CoreApiClient {

	private final RestClient restClient;
	private final Resilience resilience;

	public CoreApiClient(@Qualifier("coreApi") RestClient restClient, Resilience resilience) {
		this.restClient = restClient;
		this.resilience = resilience;
	}

	public MatchingProfile matchingProfile(String userId) {
		try {
			MatchingProfile profile = resilience.call("core-api", () -> restClient.get()
					.uri("/internal/v1/users/{userId}/matching-profile", userId)
					.retrieve()
					.body(MatchingProfile.class));
			if (profile == null) {
				throw new UpstreamException("core-api returned an empty profile.");
			}
			return profile;
		}
		catch (HttpClientErrorException ex) {
			if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
				throw new UpstreamException("Your profile was not found.", ex);
			}
			throw new UpstreamException("core-api refused the profile request (HTTP " + ex.getStatusCode().value() + ").", ex);
		}
		catch (RestClientException | DependencyUnavailableException ex) {
			throw new UpstreamException("core-api is unavailable right now. Try again shortly.", ex);
		}
	}

}
