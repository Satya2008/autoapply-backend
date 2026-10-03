package com.naukriradar.matching.client;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class JobServiceClient {

	private static final ParameterizedTypeReference<List<CandidateJob>> CANDIDATES = new ParameterizedTypeReference<>() {
	};

	private final RestClient restClient;

	public JobServiceClient(@Qualifier("jobService") RestClient restClient) {
		this.restClient = restClient;
	}

	/** Active jobs most relevant to the keywords, newest first among equals. */
	public List<CandidateJob> candidates(List<String> keywords, int postedWithinDays, int limit) {
		try {
			List<CandidateJob> jobs = restClient.post()
					.uri("/internal/v1/jobs/candidates")
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("keywords", keywords, "postedWithinDays", postedWithinDays, "limit", limit))
					.retrieve()
					.body(CANDIDATES);
			return jobs == null ? List.of() : jobs;
		}
		catch (RestClientException ex) {
			throw new UpstreamException("job-service is unavailable right now. Try again shortly.", ex);
		}
	}

}
