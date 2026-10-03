package com.naukriradar.job.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.naukriradar.job.dto.request.JobSourceRequest;
import com.naukriradar.job.exception.InvalidSourceConfigException;
import com.naukriradar.job.model.JobField;
import com.naukriradar.job.model.RequestMethod;
import com.naukriradar.job.provider.SettingPlaceholderResolver;
import org.springframework.stereotype.Component;

/**
 * Catches source definitions that can never work before they are saved, so the first sign
 * of a typo isn't a failed run at 3 a.m. Collects every problem instead of stopping at one.
 */
@Component
public class JobSourceValidator {

	/** {name} tokens only, so JSON braces in a body template aren't mistaken for variables. */
	private static final Pattern URL_VARIABLE = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");

	private static final Set<String> KNOWN_VARIABLES = Set.of("query", "page");

	/** Headers the HTTP client sets itself and refuses to take from us. */
	private static final Set<String> RESTRICTED_HEADERS = Set.of("host", "connection", "content-length", "expect",
			"upgrade", "transfer-encoding", "te", "trailer");

	private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

	public void validate(JobSourceRequest request) {
		List<String> problems = new ArrayList<>();
		checkBaseUrl(request.baseUrl(), problems);
		checkVariables("searchPath", request.searchPath(), problems);
		if (request.queryParams() != null) {
			request.queryParams().forEach((name, value) -> checkVariables("query param " + name, value, problems));
		}
		checkHeaders(request.headers(), problems);
		checkBody(request, problems);
		checkJsonPath("resultsPath", request.resultsPath(), problems);
		checkFieldMappings(request.fieldMappings(), problems);
		if (!problems.isEmpty()) {
			throw new InvalidSourceConfigException(problems);
		}
	}

	private static void checkBaseUrl(String baseUrl, List<String> problems) {
		try {
			URI uri = new URI(baseUrl.strip());
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			if (!scheme.equals("http") && !scheme.equals("https")) {
				problems.add("baseUrl must start with http:// or https://");
			}
			else if (uri.getHost() == null) {
				problems.add("baseUrl has no host");
			}
			if (uri.getQuery() != null) {
				problems.add("put query parameters in queryParams, not in baseUrl");
			}
		}
		catch (URISyntaxException ex) {
			problems.add("baseUrl is not a valid URL");
		}
	}

	private static void checkVariables(String where, String value, List<String> problems) {
		if (value == null) {
			return;
		}
		Matcher matcher = URL_VARIABLE.matcher(value);
		while (matcher.find()) {
			if (!KNOWN_VARIABLES.contains(matcher.group(1))) {
				problems.add(where + " uses {" + matcher.group(1) + "}; only {query} and {page} are filled in");
			}
		}
	}

	private static void checkHeaders(Map<String, String> headers, List<String> problems) {
		if (headers == null) {
			return;
		}
		headers.forEach((name, value) -> {
			String trimmed = name.strip();
			if (!HEADER_NAME.matcher(trimmed).matches()) {
				problems.add("header name '" + name + "' is not valid");
			}
			else if (RESTRICTED_HEADERS.contains(trimmed.toLowerCase(Locale.ROOT))) {
				problems.add("header " + trimmed + " is set automatically and can't be configured");
			}
			String withoutSettings = SettingPlaceholderResolver.PLACEHOLDER.matcher(value).replaceAll("");
			if (withoutSettings.contains("${")) {
				problems.add("header " + trimmed + " has a placeholder that isn't ${setting:key}");
			}
			if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
				problems.add("header " + trimmed + " contains a line break");
			}
		});
	}

	private static void checkBody(JobSourceRequest request, List<String> problems) {
		if (request.method() == RequestMethod.GET && request.bodyTemplate() != null && !request.bodyTemplate().isBlank()) {
			problems.add("bodyTemplate is only used with POST");
		}
		checkVariables("bodyTemplate", request.bodyTemplate(), problems);
	}

	private static void checkFieldMappings(Map<String, String> mappings, List<String> problems) {
		for (JobField field : JobField.values()) {
			if (field.isRequired() && !mappings.containsKey(field.getKey())) {
				problems.add("fieldMappings needs " + field.getKey());
			}
		}
		mappings.forEach((key, path) -> {
			if (JobField.fromKey(key.strip()).isEmpty()) {
				problems.add("fieldMappings has unknown field '" + key + "'");
			}
			checkJsonPath("fieldMappings." + key, path, problems);
		});
	}

	private static void checkJsonPath(String where, String path, List<String> problems) {
		if (path == null || !path.strip().startsWith("$")) {
			problems.add(where + " must be a JsonPath starting with $");
			return;
		}
		try {
			JsonPath.compile(path.strip());
		}
		catch (InvalidPathException | IllegalArgumentException ex) {
			problems.add(where + " is not a valid JsonPath: " + ex.getMessage());
		}
	}

}
