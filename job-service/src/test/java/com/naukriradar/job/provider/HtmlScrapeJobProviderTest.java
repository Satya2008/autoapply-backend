package com.naukriradar.job.provider;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.naukriradar.common.resilience.Resilience;
import com.naukriradar.common.resilience.ResilienceProperties;
import com.naukriradar.job.config.JobsProperties;
import com.naukriradar.job.exception.JobSourceFetchException;
import com.naukriradar.job.model.JobField;
import com.naukriradar.job.model.JobSource;
import com.naukriradar.job.model.SourceType;
import com.naukriradar.job.support.TestSources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HtmlScrapeJobProviderTest {

	@RegisterExtension
	static WireMockExtension site = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	private static final String PAGE = """
			<html><body>
			<ul class="jobs">
			  <li data-id="101"><h3>Backend Engineer</h3><span class="loc">Bengaluru</span>
			    <a class="apply" href="/careers/101">Apply</a><div class="about"><p>Java</p><p>Kafka</p></div></li>
			  <li data-id="102"><h3>Data Analyst</h3><a class="apply" href="https://other.example.com/102">Apply</a></li>
			</ul>
			</body></html>
			""";

	@Test
	void readsEachItemWithSelectorsAttributesAndFixedValues() {
		site.stubFor(get(urlPathEqualTo("/robots.txt")).willReturn(aResponse().withStatus(404)));
		site.stubFor(get(urlPathEqualTo("/careers")).willReturn(aResponse().withHeader("Content-Type", "text/html").withBody(PAGE)));

		List<RawJob> jobs = provider().fetchPage(careers(), new FetchRequest("", 1));

		assertThat(jobs).hasSize(2);
		RawJob first = jobs.get(0);
		assertThat(first.get(JobField.EXTERNAL_ID)).isEqualTo("101");
		assertThat(first.get(JobField.TITLE)).isEqualTo("Backend Engineer");
		assertThat(first.get(JobField.COMPANY)).isEqualTo("Acme");
		assertThat(first.get(JobField.LOCATION)).isEqualTo("Bengaluru");
		assertThat(first.get(JobField.APPLY_URL)).isEqualTo(site.baseUrl() + "/careers/101");
		assertThat((String) first.get(JobField.DESCRIPTION)).contains("<p>Java</p>");
		assertThat(jobs.get(1).get(JobField.LOCATION)).isNull();
		assertThat(jobs.get(1).get(JobField.APPLY_URL)).isEqualTo("https://other.example.com/102");
	}

	@Test
	void aPageRobotsTxtDisallowsIsNeverFetched() {
		site.stubFor(get(urlPathEqualTo("/robots.txt")).willReturn(aResponse().withBody("User-agent: *\nDisallow: /careers\n")));
		site.stubFor(get(urlPathEqualTo("/careers")).willReturn(aResponse().withBody(PAGE)));

		assertThatThrownBy(() -> provider().fetchPage(careers(), new FetchRequest("", 1)))
				.isInstanceOf(JobSourceFetchException.class)
				.hasMessageContaining("robots.txt");
		site.verify(0, getRequestedFor(urlPathEqualTo("/careers")));
	}

	private JobSource careers() {
		JobSource source = TestSources.source("acme", site.baseUrl(), "/careers", "ul.jobs > li");
		source.setType(SourceType.HTML_SCRAPE);
		source.getFieldMappings().put("externalId", "@data-id");
		source.getFieldMappings().put("title", "h3");
		source.getFieldMappings().put("company", "=Acme");
		source.getFieldMappings().put("location", ".loc");
		source.getFieldMappings().put("applyUrl", "a.apply@href");
		source.getFieldMappings().put("description", ".about@html");
		return source;
	}

	private static HtmlScrapeJobProvider provider() {
		JobsProperties properties = new JobsProperties(true, DataSize.ofMegabytes(5), Duration.ofSeconds(2), 3,
				"NaukriRadar/1.0 (+test)", false);
		PageFetcher fetcher = new PageFetcher(RestClient.builder(), HttpClient.newHttpClient(), properties);
		return new HtmlScrapeJobProvider(fetcher, new RobotsTxtPolicy(fetcher, properties),
				new SettingPlaceholderResolver(new MockEnvironment()), new HostGuard(properties),
				new Resilience(new ResilienceProperties(3, Duration.ofMillis(10), 0.5, 50, 20, 10, Duration.ofSeconds(30), 10)));
	}

}
