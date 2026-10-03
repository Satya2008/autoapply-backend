package com.naukriradar.job.mapper;

import java.util.LinkedHashMap;
import java.util.Map;

import com.naukriradar.job.dto.request.JobSourceRequest;
import com.naukriradar.job.dto.response.JobSourceResponse;
import com.naukriradar.job.model.JobSource;
import com.naukriradar.job.provider.SettingPlaceholderResolver;
import org.springframework.stereotype.Component;

@Component
public class JobSourceMapper {

	/** Shown instead of header values typed in literally, and accepted back on PUT. */
	public static final String MASK = "****";

	/** Copies everything editable. A header sent back as {@link #MASK} keeps its stored value. */
	public void apply(JobSourceRequest request, JobSource source) {
		Map<String, String> oldHeaders = new LinkedHashMap<>(source.getHeaders());

		source.setName(request.name().strip());
		source.setType(request.type());
		source.setBaseUrl(stripTrailingSlash(request.baseUrl().strip()));
		source.setSearchPath(blankToNull(request.searchPath()));
		source.setMethod(request.method());
		source.setBodyTemplate(blankToNull(request.bodyTemplate()));
		source.setResultsPath(request.resultsPath().strip());
		source.setEnabled(request.enabled());
		source.setPriority(request.priority());
		source.setTimeoutSeconds(request.timeoutSeconds());
		source.setMaxPages(request.maxPages());

		source.getHeaders().clear();
		if (request.headers() != null) {
			request.headers().forEach((name, value) -> {
				String kept = MASK.equals(value) ? oldHeaders.get(name.strip()) : value;
				if (kept != null) {
					source.getHeaders().put(name.strip(), kept);
				}
			});
		}
		replace(source.getQueryParams(), request.queryParams());
		replace(source.getFieldMappings(), request.fieldMappings());
	}

	public JobSourceResponse toResponse(JobSource source, long jobCount) {
		Map<String, String> headers = new LinkedHashMap<>();
		source.getHeaders().forEach((name, value) -> headers.put(name, maskUnlessPlaceholder(value)));
		return new JobSourceResponse(
				source.getId(),
				source.getCode(),
				source.getName(),
				source.getType(),
				source.getBaseUrl(),
				source.getSearchPath(),
				source.getMethod(),
				source.getBodyTemplate(),
				headers,
				new LinkedHashMap<>(source.getQueryParams()),
				source.getResultsPath(),
				new LinkedHashMap<>(source.getFieldMappings()),
				source.isEnabled(),
				source.getPriority(),
				source.getTimeoutSeconds(),
				source.getMaxPages(),
				source.getLastRunAt(),
				source.getLastRunStatus(),
				source.getLastRunMessage(),
				source.getConsecutiveFailures(),
				jobCount);
	}

	/**
	 * A value that is a ${setting:...} reference, optionally after a scheme word such as
	 * "Bearer", holds no secret and can be shown. Anything else is masked.
	 */
	private static String maskUnlessPlaceholder(String value) {
		String rest = SettingPlaceholderResolver.PLACEHOLDER.matcher(value).replaceAll("");
		boolean hasReference = !rest.equals(value);
		return hasReference && rest.strip().matches("[A-Za-z]{0,10}") ? value : MASK;
	}

	private static void replace(Map<String, String> target, Map<String, String> values) {
		target.clear();
		if (values != null) {
			values.forEach((key, value) -> target.put(key.strip(), value.strip()));
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	private static String stripTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

}
