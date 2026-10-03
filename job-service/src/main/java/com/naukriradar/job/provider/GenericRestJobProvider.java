package com.naukriradar.job.provider;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import com.naukriradar.job.config.JobsProperties;
import com.naukriradar.job.exception.JobSourceFetchException;
import com.naukriradar.job.model.JobField;
import com.naukriradar.job.model.JobSource;
import com.naukriradar.job.model.RequestMethod;
import com.naukriradar.job.model.SourceType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Handles any board that is "an HTTP call returning JSON". Everything board-specific (URL,
 * params, headers, where the list is, where each field is) comes from the {@link JobSource} row.
 */
@Component
public class GenericRestJobProvider implements JobSourceProvider {

	/** Missing fields read as null instead of throwing; boards often omit optional ones. */
	private static final Configuration LENIENT = Configuration.defaultConfiguration()
			.addOptions(Option.SUPPRESS_EXCEPTIONS);

	private static final Configuration STRICT = Configuration.defaultConfiguration();

	private final RestClient.Builder restClientBuilder;
	private final HttpClient httpClient;
	private final SettingPlaceholderResolver placeholders;
	private final HostGuard hostGuard;
	private final JobsProperties properties;

	public GenericRestJobProvider(RestClient.Builder restClientBuilder, HttpClient httpClient,
			SettingPlaceholderResolver placeholders, HostGuard hostGuard, JobsProperties properties) {
		this.restClientBuilder = restClientBuilder;
		this.httpClient = httpClient;
		this.placeholders = placeholders;
		this.hostGuard = hostGuard;
		this.properties = properties;
	}

	@Override
	public SourceType supports() {
		return SourceType.REST_JSON;
	}

	@Override
	public List<RawJob> fetchPage(JobSource source, FetchRequest request) {
		URI uri = buildUri(source, request);
		hostGuard.check(uri);
		String body = call(source, uri, request);
		return extract(source, body);
	}

	URI buildUri(JobSource source, FetchRequest request) {
		Map<String, Object> variables = Map.of("query", request.query(), "page", request.page());
		try {
			UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(source.getBaseUrl() + nullToEmpty(source.getSearchPath()));
			source.getQueryParams().forEach(builder::queryParam);
			// encode the template first, then expand: values like "c++ & go" are encoded strictly
			return builder.encode().buildAndExpand(variables).toUri();
		}
		catch (IllegalArgumentException ex) {
			throw new JobSourceFetchException("Could not build the request URL: " + ex.getMessage(), ex);
		}
	}

	private String call(JobSource source, URI uri, FetchRequest request) {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(source.getTimeoutSeconds()));
		RestClient client = restClientBuilder.clone().requestFactory(requestFactory).build();

		long maxBytes = properties.maxResponseSize().toBytes();
		try {
			RestClient.RequestBodySpec spec = client.method(HttpMethod.valueOf(source.getMethod().name()))
					.uri(uri)
					.accept(MediaType.APPLICATION_JSON)
					.header(HttpHeaders.USER_AGENT, properties.userAgent())
					.headers(headers -> source.getHeaders().forEach((name, value) -> headers.set(name, placeholders.resolve(value))));
			if (source.getMethod() == RequestMethod.POST && source.getBodyTemplate() != null) {
				spec.contentType(MediaType.APPLICATION_JSON).body(fillBody(source.getBodyTemplate(), request));
			}
			byte[] bytes = spec.exchange((req, response) -> {
				if (response.getStatusCode().isError()) {
					throw new JobSourceFetchException("The board answered HTTP " + response.getStatusCode().value() + ".");
				}
				try (InputStream in = response.getBody()) {
					byte[] read = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
					if (read.length > maxBytes) {
						throw new JobSourceFetchException("The response is larger than " + properties.maxResponseSize() + ".");
					}
					return read;
				}
			});
			return new String(bytes, StandardCharsets.UTF_8);
		}
		catch (JobSourceFetchException ex) {
			throw ex;
		}
		catch (RestClientException ex) {
			Throwable root = rootCause(ex);
			String reason = root instanceof HttpTimeoutException ? "timed out after "
					+ source.getTimeoutSeconds() + "s" : root.getClass().getSimpleName();
			throw new JobSourceFetchException("Could not reach " + uri.getHost() + ": " + reason + ".", ex);
		}
	}

	List<RawJob> extract(JobSource source, String body) {
		DocumentContext document;
		try {
			document = JsonPath.using(STRICT).parse(body);
		}
		catch (InvalidJsonException | IllegalArgumentException ex) {
			throw new JobSourceFetchException("The response is not valid JSON.", ex);
		}
		// json-smart is lenient and reads an HTML error page as a bare string; a board
		// response is always an object or an array
		Object root = document.json();
		if (!(root instanceof Map<?, ?>) && !(root instanceof List<?>)) {
			throw new JobSourceFetchException("The response is not valid JSON.");
		}

		Object results;
		try {
			results = document.read(source.getResultsPath());
		}
		catch (PathNotFoundException ex) {
			// some boards drop the list entirely on the last page
			return List.of();
		}
		catch (JsonPathException ex) {
			throw new JobSourceFetchException("Could not read " + source.getResultsPath() + ": " + ex.getMessage(), ex);
		}
		if (results == null) {
			return List.of();
		}
		if (!(results instanceof List<?> items)) {
			throw new JobSourceFetchException("results path " + source.getResultsPath() + " is not a list.");
		}

		List<RawJob> jobs = new ArrayList<>(items.size());
		for (Object item : items) {
			if (item == null) {
				continue;
			}
			DocumentContext itemContext = JsonPath.using(LENIENT).parse(item);
			Map<JobField, Object> values = new EnumMap<>(JobField.class);
			source.getFieldMappings().forEach((key, path) ->
					JobField.fromKey(key).ifPresent(field -> values.put(field, itemContext.read(path))));
			jobs.add(new RawJob(values));
		}
		return jobs;
	}

	/** Puts query and page into a JSON body template, escaped as JSON strings. */
	private static String fillBody(String template, FetchRequest request) {
		return template.replace("{query}", jsonEscape(request.query()))
				.replace("{page}", Integer.toString(request.page()));
	}

	private static String jsonEscape(String value) {
		StringBuilder out = new StringBuilder(value.length());
		for (char c : value.toCharArray()) {
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if (c < 0x20) {
						out.append(String.format("\\u%04x", (int) c));
					}
					else {
						out.append(c);
					}
				}
			}
		}
		return out.toString();
	}

	private static Throwable rootCause(Throwable ex) {
		Throwable cause = ex;
		while (cause.getCause() != null && cause.getCause() != cause) {
			cause = cause.getCause();
		}
		return cause;
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

}
