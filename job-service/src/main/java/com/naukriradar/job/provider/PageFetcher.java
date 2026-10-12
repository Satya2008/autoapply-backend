package com.naukriradar.job.provider;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import com.naukriradar.job.config.JobsProperties;
import com.naukriradar.job.exception.JobSourceFetchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * A plain GET for HTML pages and robots.txt, with the same limits as the JSON boards: our own
 * user agent, a read timeout and a response size cap. Returns the status and body as they
 * came, so the caller decides what a 404 or a 503 means.
 */
@Component
public class PageFetcher {

	private final RestClient.Builder restClientBuilder;
	private final HttpClient httpClient;
	private final JobsProperties properties;

	public PageFetcher(RestClient.Builder restClientBuilder, HttpClient httpClient, JobsProperties properties) {
		this.restClientBuilder = restClientBuilder;
		this.httpClient = httpClient;
		this.properties = properties;
	}

	public record Page(int status, String body) {

		public boolean ok() {
			return status >= 200 && status < 300;
		}

	}

	public Page get(URI uri, String accept, Map<String, String> headers, int timeoutSeconds) {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
		RestClient client = restClientBuilder.clone().requestFactory(requestFactory).build();
		long maxBytes = properties.maxResponseSize().toBytes();
		try {
			return client.get()
					.uri(uri)
					.header(HttpHeaders.ACCEPT, accept)
					.header(HttpHeaders.USER_AGENT, properties.userAgent())
					.headers(h -> headers.forEach(h::set))
					.exchange((req, response) -> {
						try (InputStream in = response.getBody()) {
							byte[] read = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
							if (read.length > maxBytes) {
								throw new JobSourceFetchException("The page is larger than " + properties.maxResponseSize() + ".");
							}
							return new Page(response.getStatusCode().value(), new String(read, StandardCharsets.UTF_8));
						}
					});
		}
		catch (JobSourceFetchException ex) {
			throw ex;
		}
		catch (RestClientException ex) {
			Throwable root = ex;
			while (root.getCause() != null && root.getCause() != root) {
				root = root.getCause();
			}
			String reason = root instanceof HttpTimeoutException ? "timed out after " + timeoutSeconds + "s"
					: root.getClass().getSimpleName();
			throw new JobSourceFetchException("Could not reach " + uri.getHost() + ": " + reason + ".", ex);
		}
	}

}
