package com.naukriradar.core.client;

import java.net.http.HttpClient;
import java.util.Optional;

import com.naukriradar.common.resilience.DependencyUnavailableException;
import com.naukriradar.common.resilience.Resilience;
import com.naukriradar.core.config.ServicesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Job postings from job-service, for writing about a job (cover letters, screening answers). */
@Component
public class JobServiceClient {

	private static final Logger log = LoggerFactory.getLogger(JobServiceClient.class);

	private final RestClient client;
	private final Resilience resilience;

	public JobServiceClient(RestClient.Builder builder, ServicesProperties properties, Resilience resilience) {
		// HTTP/1.1: between services on plain http an HTTP/2 upgrade buys nothing and some servers drop it
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1).connectTimeout(properties.connectTimeout()).build());
		factory.setReadTimeout(properties.readTimeout());
		this.client = builder.clone().requestFactory(factory).baseUrl(properties.jobServiceUrl()).build();
		this.resilience = resilience;
	}

	/**
	 * The posting, or empty when job-service doesn't know it or can't be reached: the caller
	 * then writes from the title and company alone instead of failing.
	 */
	public Optional<JobPosting> job(String jobId) {
		try {
			return Optional.ofNullable(resilience.call("job-service", () -> client.get()
					.uri("/internal/v1/jobs/{id}", jobId)
					.retrieve()
					.body(JobPosting.class)));
		}
		catch (HttpClientErrorException.NotFound ex) {
			return Optional.empty();
		}
		catch (RestClientException | DependencyUnavailableException ex) {
			log.info("Job {} not available from job-service: {}", jobId, ex.getMessage());
			return Optional.empty();
		}
	}

}
