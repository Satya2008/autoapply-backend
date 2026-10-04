package com.naukriradar.job.provider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.naukriradar.common.resilience.Resilience;
import com.naukriradar.common.resilience.ResilienceProperties;
import com.naukriradar.job.config.JobsProperties;
import com.naukriradar.job.exception.JobSourceFetchException;
import com.naukriradar.job.model.JobField;
import com.naukriradar.job.model.JobSource;
import com.naukriradar.job.model.RequestMethod;
import com.naukriradar.job.support.TestSources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenericRestJobProviderTest {

	@RegisterExtension
	static WireMockExtension board = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	private final MockEnvironment environment = new MockEnvironment()
			.withProperty("naukriradar.settings.board-key", "secret-123");

	@Test
	void mapsEveryItemOfTheResultsArray() throws IOException {
		board.stubFor(get(urlPathEqualTo("/api/job-board-api")).willReturn(okJson(fixture("arbeitnow-page1.json"))));

		List<RawJob> jobs = provider(true).fetchPage(arbeitnow(), new FetchRequest("", 1));

		assertThat(jobs).hasSize(3);
		RawJob first = jobs.get(0);
		assertThat(first.get(JobField.EXTERNAL_ID)).isEqualTo("java-backend-developer-berlin-1");
		assertThat(first.get(JobField.COMPANY)).isEqualTo("Acme GmbH");
		assertThat(first.get(JobField.REMOTE)).isEqualTo(false);
		assertThat(first.get(JobField.POSTED_AT)).isEqualTo(1790000000);
		board.verify(getRequestedFor(urlPathEqualTo("/api/job-board-api")).withQueryParam("page", equalTo("1")));
	}

	@Test
	void missingOptionalFieldsAreNull() {
		board.stubFor(get(urlPathEqualTo("/api/job-board-api"))
				.willReturn(okJson("{\"data\": [{\"slug\": \"a\", \"title\": \"T\"}]}")));

		RawJob job = provider(true).fetchPage(arbeitnow(), new FetchRequest("", 1)).get(0);

		assertThat(job.get(JobField.COMPANY)).isNull();
		assertThat(job.get(JobField.LOCATION)).isNull();
	}

	@Test
	void anEmptyOrMissingListMeansNoMoreJobs() {
		board.stubFor(get(urlPathEqualTo("/api/job-board-api")).willReturn(okJson("{\"data\": []}")));
		assertThat(provider(true).fetchPage(arbeitnow(), new FetchRequest("", 2))).isEmpty();

		board.stubFor(get(urlPathEqualTo("/api/job-board-api")).willReturn(okJson("{\"meta\": {}}")));
		assertThat(provider(true).fetchPage(arbeitnow(), new FetchRequest("", 3))).isEmpty();
	}

	@Test
	void queryAndPageAreEncodedIntoTheUrl() {
		JobSource source = arbeitnow();
		source.getQueryParams().clear();
		source.getQueryParams().put("q", "{query}");
		source.getQueryParams().put("p", "{page}");

		URI uri = provider(true).buildUri(source, new FetchRequest("c++ & go/rust", 7));

		assertThat(uri.getRawQuery()).isEqualTo("q=c%2B%2B%20%26%20go%2Frust&p=7");
	}

	@Test
	void settingPlaceholdersInHeadersAreResolved() {
		board.stubFor(get(urlPathEqualTo("/api/job-board-api")).willReturn(okJson("{\"data\": []}")));
		JobSource source = arbeitnow();
		source.getHeaders().put("Authorization", "Bearer ${setting:board-key}");

		provider(true).fetchPage(source, new FetchRequest("", 1));

		board.verify(getRequestedFor(urlPathEqualTo("/api/job-board-api"))
				.withHeader("Authorization", equalTo("Bearer secret-123"))
				.withHeader("User-Agent", equalTo("test-agent")));
	}

	@Test
	void anUnsetSettingIsNeverSentLiterally() {
		JobSource source = arbeitnow();
		source.getHeaders().put("X-Api-Key", "${setting:missing-key}");

		assertThatThrownBy(() -> provider(true).fetchPage(source, new FetchRequest("", 1)))
				.isInstanceOf(JobSourceFetchException.class)
				.hasMessageContaining("missing-key");
		assertThat(board.getAllServeEvents()).isEmpty();
	}

	@Test
	void postSourcesSendTheFilledBodyTemplate() {
		board.stubFor(post(urlPathEqualTo("/search")).willReturn(okJson("{\"results\": []}")));
		JobSource source = TestSources.source("poster", board.baseUrl(), "/search", "$.results");
		source.setMethod(RequestMethod.POST);
		source.setBodyTemplate("{\"q\": \"{query}\", \"page\": {page}}");

		provider(true).fetchPage(source, new FetchRequest("say \"hi\"", 2));

		board.verify(postRequestedFor(urlPathEqualTo("/search"))
				.withRequestBody(equalToJson("{\"q\": \"say \\\"hi\\\"\", \"page\": 2}")));
	}

	@Test
	void serverErrorsBecomeAFetchFailure() {
		board.stubFor(get(urlPathEqualTo("/api/job-board-api")).willReturn(aResponse().withStatus(503)));

		assertThatThrownBy(() -> provider(true).fetchPage(arbeitnow(), new FetchRequest("", 1)))
				.isInstanceOf(JobSourceFetchException.class)
				.hasMessageContaining("503");
	}

	@Test
	void aBoardThatFailsBrieflyIsRetriedAndTheFetchSucceeds() throws IOException {
		board.stubFor(get(urlPathEqualTo("/api/job-board-api")).inScenario("flaky").whenScenarioStateIs(STARTED)
				.willReturn(aResponse().withStatus(503)).willSetStateTo("recovering"));
		board.stubFor(get(urlPathEqualTo("/api/job-board-api")).inScenario("flaky").whenScenarioStateIs("recovering")
				.willReturn(aResponse().withStatus(502)).willSetStateTo("up"));
		board.stubFor(get(urlPathEqualTo("/api/job-board-api")).inScenario("flaky").whenScenarioStateIs("up")
				.willReturn(okJson(fixture("arbeitnow-page1.json"))));

		assertThat(provider(true).fetchPage(arbeitnow(), new FetchRequest("", 1))).isNotEmpty();
		board.verify(3, getRequestedFor(urlPathEqualTo("/api/job-board-api")));
	}

	@Test
	void slowBoardsTimeOut() {
		board.stubFor(get(urlPathEqualTo("/api/job-board-api"))
				.willReturn(okJson("{\"data\": []}").withFixedDelay(2_500)));
		JobSource source = arbeitnow();
		source.setTimeoutSeconds(1);

		assertThatThrownBy(() -> provider(true).fetchPage(source, new FetchRequest("", 1)))
				.isInstanceOf(JobSourceFetchException.class)
				.hasMessageContaining("timed out");
	}

	@Test
	void invalidJsonIsReported() {
		board.stubFor(get(urlPathEqualTo("/api/job-board-api"))
				.willReturn(aResponse().withHeader("Content-Type", "text/html").withBody("<html>maintenance</html>")));

		assertThatThrownBy(() -> provider(true).fetchPage(arbeitnow(), new FetchRequest("", 1)))
				.isInstanceOf(JobSourceFetchException.class)
				.hasMessageContaining("not valid JSON");
	}

	@Test
	void resultsPathMustPointAtAList() {
		board.stubFor(get(urlPathEqualTo("/api/job-board-api")).willReturn(okJson("{\"data\": {\"slug\": \"x\"}}")));

		assertThatThrownBy(() -> provider(true).fetchPage(arbeitnow(), new FetchRequest("", 1)))
				.isInstanceOf(JobSourceFetchException.class)
				.hasMessageContaining("is not a list");
	}

	@Test
	void hugeResponsesAreRefused() {
		board.stubFor(get(urlPathEqualTo("/api/job-board-api"))
				.willReturn(okJson("{\"data\": [], \"pad\": \"" + "x".repeat(4_000) + "\"}")));
		JobsProperties tiny = new JobsProperties(true, DataSize.ofBytes(1_000), Duration.ofSeconds(2), 3, "test-agent", false);

		assertThatThrownBy(() -> provider(tiny).fetchPage(arbeitnow(), new FetchRequest("", 1)))
				.isInstanceOf(JobSourceFetchException.class)
				.hasMessageContaining("larger than");
	}

	@Test
	void privateHostsAreBlockedUnlessAllowed() {
		assertThatThrownBy(() -> provider(false).fetchPage(arbeitnow(), new FetchRequest("", 1)))
				.isInstanceOf(JobSourceFetchException.class)
				.hasMessageContaining("private address");
		assertThat(board.getAllServeEvents()).isEmpty();
	}

	@Test
	void onlyHttpUrlsAreCalled() {
		JobSource source = TestSources.source("ftp", "ftp://example.com", "/jobs", "$.data");

		assertThatThrownBy(() -> provider(true).fetchPage(source, new FetchRequest("", 1)))
				.isInstanceOf(JobSourceFetchException.class)
				.hasMessageContaining("http");
	}

	private JobSource arbeitnow() {
		JobSource source = TestSources.source("arbeitnow", board.baseUrl(), "/api/job-board-api", "$.data");
		source.getQueryParams().put("page", "{page}");
		source.getFieldMappings().putAll(Map.of(
				"externalId", "$.slug",
				"title", "$.title",
				"company", "$.company_name",
				"location", "$.location",
				"remote", "$.remote",
				"applyUrl", "$.url",
				"postedAt", "$.created_at"));
		return source;
	}

	private GenericRestJobProvider provider(boolean allowPrivateHosts) {
		return provider(new JobsProperties(allowPrivateHosts, DataSize.ofMegabytes(5), Duration.ofSeconds(2), 3,
				"test-agent", false));
	}

	private GenericRestJobProvider provider(JobsProperties properties) {
		return new GenericRestJobProvider(RestClient.builder(), HttpClient.newHttpClient(),
				new SettingPlaceholderResolver(environment), new HostGuard(properties), properties,
				new Resilience(new ResilienceProperties(3, Duration.ofMillis(10), 0.5, 50, 20, 10, Duration.ofSeconds(30), 10)));
	}

	private static String fixture(String name) throws IOException {
		return new ClassPathResource("boards/" + name).getContentAsString(StandardCharsets.UTF_8);
	}

}
