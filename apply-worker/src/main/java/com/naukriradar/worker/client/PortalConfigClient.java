package com.naukriradar.worker.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.naukriradar.common.resilience.Resilience;
import com.naukriradar.worker.config.ServicesProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Portal form mappings, from core-api (which owns them) and cached here for a few minutes:
 * a worker filling a hundred forms on one portal asks once.
 */
@Component
public class PortalConfigClient {

	private final RestClient client;
	private final Resilience resilience;
	private final Cache<String, Optional<PortalConfig>> cache = Caffeine.newBuilder()
			.expireAfterWrite(Duration.ofMinutes(5))
			.maximumSize(1_000)
			.build();

	public PortalConfigClient(RestClient.Builder builder, ServicesProperties properties, Resilience resilience) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1).connectTimeout(properties.connectTimeout()).build());
		factory.setReadTimeout(properties.readTimeout());
		this.client = builder.clone().requestFactory(factory).baseUrl(properties.coreApiUrl()).build();
		this.resilience = resilience;
	}

	/** The most specific portal covering the apply link's host; empty when none is set up. */
	public Optional<PortalConfig> forUrl(String applyUrl) {
		String host;
		try {
			host = URI.create(applyUrl).getHost();
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
		if (host == null) {
			return Optional.empty();
		}
		return cache.get(host.toLowerCase(), this::fetch);
	}

	private Optional<PortalConfig> fetch(String host) {
		try {
			return Optional.ofNullable(resilience.call("core-api", () -> client.get()
					.uri(uri -> uri.path("/internal/v1/portals").queryParam("domain", host).build())
					.retrieve()
					.body(PortalConfig.class)));
		}
		catch (HttpClientErrorException ex) {
			if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
				return Optional.empty();
			}
			throw ex;
		}
	}

}
