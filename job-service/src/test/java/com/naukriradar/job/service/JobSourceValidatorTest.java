package com.naukriradar.job.service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.naukriradar.job.dto.request.JobSourceRequest;
import com.naukriradar.job.exception.InvalidSourceConfigException;
import com.naukriradar.job.model.RequestMethod;
import com.naukriradar.job.model.SourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobSourceValidatorTest {

	private final JobSourceValidator validator = new JobSourceValidator();

	@Test
	void acceptsAGoodSource() {
		assertThatCode(() -> validator.validate(request(b -> {
		}))).doesNotThrowAnyException();
	}

	@Test
	void fixedValuesAreAcceptedInPlaceOfAPath() {
		assertThatCode(() -> validator.validate(request(b -> b.mappings.put("company", "=Acme"))))
				.doesNotThrowAnyException();
		assertThatThrownBy(() -> validator.validate(request(b -> b.mappings.put("company", "="))))
				.isInstanceOf(InvalidSourceConfigException.class)
				.hasMessageContaining("fixed value");
	}

	@Test
	void scrapedSourcesUseCssSelectors() {
		assertThatCode(() -> validator.validate(request(b -> {
			b.type = SourceType.HTML_SCRAPE;
			b.resultsPath = "ul.jobs > li";
			b.mappings = new HashMap<>(Map.of("externalId", "a@href", "title", "h3", "company", "=Acme",
					"applyUrl", "a.apply@href", "description", ".about@html"));
		}))).doesNotThrowAnyException();

		assertThatThrownBy(() -> validator.validate(request(b -> {
			b.type = SourceType.HTML_SCRAPE;
			b.resultsPath = "ul.jobs >> [";
			b.method = RequestMethod.POST;
		})))
				.isInstanceOf(InvalidSourceConfigException.class)
				.hasMessageContaining("CSS selector")
				.hasMessageContaining("GET");
	}

	@Test
	void reportsEveryProblemAtOnce() {
		assertThatThrownBy(() -> validator.validate(request(b -> {
			b.baseUrl = "ftp://boards.example.com";
			b.resultsPath = "data";
			b.mappings.remove("applyUrl");
		})))
				.isInstanceOf(InvalidSourceConfigException.class)
				.hasMessageContaining("http")
				.hasMessageContaining("resultsPath")
				.hasMessageContaining("applyUrl");
	}

	@Test
	void rejectsBrokenJsonPaths() {
		assertThatThrownBy(() -> validator.validate(request(b -> b.mappings.put("title", "$.items[?(@.x =="))))
				.hasMessageContaining("fieldMappings.title");
	}

	@Test
	void rejectsUnknownFieldsAndUrlVariables() {
		assertThatThrownBy(() -> validator.validate(request(b -> {
			b.mappings.put("salary", "$.pay");
			b.searchPath = "/jobs/{country}";
		})))
				.hasMessageContaining("unknown field 'salary'")
				.hasMessageContaining("{country}");
	}

	@Test
	void rejectsQueryStringsInTheBaseUrl() {
		assertThatThrownBy(() -> validator.validate(request(b -> b.baseUrl = "https://boards.example.com?key=1")))
				.hasMessageContaining("queryParams");
	}

	@Test
	void rejectsHeadersTheClientControlsOrThatCouldInjectLines() {
		assertThatThrownBy(() -> validator.validate(request(b -> {
			b.headers.put("Host", "evil.example.com");
			b.headers.put("X-Bad", "a\r\nX-Injected: 1");
			b.headers.put("Bad Name", "x");
		})))
				.hasMessageContaining("Host is set automatically")
				.hasMessageContaining("line break")
				.hasMessageContaining("'Bad Name'");
	}

	@Test
	void onlySettingPlaceholdersAreAllowedInHeaders() {
		assertThatCode(() -> validator.validate(request(b -> b.headers.put("Authorization", "Bearer ${setting:api-key}"))))
				.doesNotThrowAnyException();
		assertThatThrownBy(() -> validator.validate(request(b -> b.headers.put("X-Key", "${env:HOME}"))))
				.hasMessageContaining("${setting:key}");
	}

	@Test
	void bodyTemplateNeedsPost() {
		assertThatThrownBy(() -> validator.validate(request(b -> b.body = "{\"q\": \"{query}\"}")))
				.hasMessageContaining("only used with POST");
		assertThatCode(() -> validator.validate(request(b -> {
			b.body = "{\"q\": \"{query}\"}";
			b.method = RequestMethod.POST;
		}))).doesNotThrowAnyException();
	}

	private static JobSourceRequest request(Consumer<Builder> changes) {
		Builder b = new Builder();
		changes.accept(b);
		return new JobSourceRequest("board", "Board", b.type, "IN", b.baseUrl, b.searchPath, b.method, b.body,
				b.headers, Map.of("page", "{page}"), b.resultsPath, b.mappings, true, 1, 10, 1, null);
	}

	private static final class Builder {

		SourceType type = SourceType.REST_JSON;

		String baseUrl = "https://boards.example.com";

		String searchPath = "/api/jobs";

		RequestMethod method = RequestMethod.GET;

		String body;

		String resultsPath = "$.data";

		Map<String, String> headers = new HashMap<>();

		Map<String, String> mappings = new HashMap<>(Map.of(
				"externalId", "$.id", "title", "$.title", "company", "$.company", "applyUrl", "$.url"));

	}

}
