package com.naukriradar.job.provider;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

import com.naukriradar.job.exception.JobSourceFetchException;
import com.naukriradar.job.model.JobSource;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Builds the URL a source calls, the same way for every source type: base URL, search path
 * (which may hold {page} and {query}), then the query parameters. The path or a parameter
 * value may also read a secret with ${setting:key}, so an API key stays out of the table.
 */
final class SourceUris {

	private SourceUris() {
	}

	static URI build(JobSource source, FetchRequest request, SettingPlaceholderResolver placeholders) {
		Map<String, Object> variables = new HashMap<>();
		variables.put("query", request.query());
		variables.put("page", request.page());
		try {
			int n = 0;
			// a key in the path (jooble.org/api/{key}) the same way: a variable, encoded
			String path = nullToEmpty(source.getSearchPath());
			Matcher inPath = SettingPlaceholderResolver.PLACEHOLDER.matcher(path);
			StringBuilder template = new StringBuilder();
			while (inPath.find()) {
				String name = "setting" + n++;
				variables.put(name, placeholders.resolve(inPath.group()));
				inPath.appendReplacement(template, Matcher.quoteReplacement("{" + name + "}"));
			}
			inPath.appendTail(template);
			UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(source.getBaseUrl() + template);
			for (Map.Entry<String, String> param : source.getQueryParams().entrySet()) {
				String value = param.getValue();
				if (SettingPlaceholderResolver.PLACEHOLDER.matcher(value).find()) {
					// resolved values go in as variables: a secret is encoded, never read as a template
					String name = "setting" + n++;
					variables.put(name, fill(placeholders.resolve(value), request));
					builder.queryParam(param.getKey(), "{" + name + "}");
				}
				else {
					builder.queryParam(param.getKey(), value);
				}
			}
			// encode the template first, then expand: values like "c++ & go" are encoded strictly
			return builder.encode().buildAndExpand(variables).toUri();
		}
		catch (IllegalArgumentException ex) {
			throw new JobSourceFetchException("Could not build the request URL: " + ex.getMessage(), ex);
		}
	}

	private static String fill(String value, FetchRequest request) {
		return value.replace("{query}", request.query()).replace("{page}", Integer.toString(request.page()));
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

}
