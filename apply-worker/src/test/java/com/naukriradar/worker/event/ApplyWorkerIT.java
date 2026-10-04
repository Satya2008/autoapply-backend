package com.naukriradar.worker.event;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.naukriradar.common.events.EventEnvelope;
import com.naukriradar.common.events.EventTopics;
import com.naukriradar.common.events.Topics;
import com.naukriradar.worker.support.FakePortal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * apply.requested -> real headless Chrome on the local fake portal -> apply.completed, with
 * WireMock playing core-api's portal API.
 */
// Fresh consumer groups reading from the latest offset, so no backlog from earlier runs reaches it;
// closed after this class so its consumers don't run on during later classes.
@DirtiesContext
@SpringBootTest(properties = { "spring.kafka.listener.auto-startup=true",
		"naukriradar.events.group-suffix=.${random.uuid}", "spring.kafka.consumer.auto-offset-reset=latest" })
@ActiveProfiles("test")
class ApplyWorkerIT {

	@RegisterExtension
	static WireMockExtension coreApi = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	private static final FakePortal portal = startPortal();

	@DynamicPropertySource
	static void pointAtFakes(DynamicPropertyRegistry registry) {
		registry.add("naukriradar.services.core-api-url", coreApi::baseUrl);
	}

	@Autowired
	private KafkaTemplate<String, String> kafka;

	@Autowired
	private EventTopics topics;

	@Autowired
	private JsonMapper json;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private KafkaListenerEndpointRegistry listeners;

	@AfterAll
	static void stop() {
		portal.close();
	}

	/** The consumers read from the latest offset, so they must be listening before a test sends. */
	@BeforeEach
	void listening() throws InterruptedException {
		coreApi.stubFor(WireMock.get(urlPathEqualTo("/internal/v1/portals")).willReturn(okJson("""
				{"domain": "localhost", "name": "Fake portal", "riskBand": "LOW",
				 "selectors": {"fullName": "#name", "email": "#email", "submit": "#send", "success": "#thanks"}}""")));
		waitUntil(() -> listeners.getListenerContainers().stream()
				.allMatch(c -> c.getAssignedPartitions() != null && !c.getAssignedPartitions().isEmpty()));
	}

	@Test
	void aRequestedApplicationIsSubmittedAndTheResultAnnounced() throws Exception {
		String applicationId = UUID.randomUUID().toString();
		int before = portal.submissions().size();

		send(applicationId, 1);

		waitUntil(() -> result(applicationId) != null);
		assertThat(result(applicationId)).contains("\"outcome\":\"SUBMITTED\"").contains("\"filled\":[\"fullName\",\"email\"]");
		assertThat(portal.submissions()).hasSize(before + 1);
		assertThat(portal.submissions().getLast()).contains("email=asha@example.com");
	}

	@Test
	void anAttemptCutShortByACrashIsNeverSubmittedTwice() throws Exception {
		String applicationId = UUID.randomUUID().toString();
		// as if a worker had started this attempt and died before reporting
		jdbc.update("INSERT INTO apply_attempts (application_id, attempt, status, started_at) VALUES (?, 2, 'STARTED', NOW(6))",
				applicationId);
		int before = portal.submissions().size();

		send(applicationId, 2);

		waitUntil(() -> result(applicationId) != null);
		assertThat(result(applicationId)).contains("\"outcome\":\"UNKNOWN\"").contains("check on the site");
		assertThat(portal.submissions()).hasSize(before);
	}

	@Test
	void aSiteWithoutAFormMappingGoesToTheCandidate() throws Exception {
		coreApi.stubFor(WireMock.get(urlPathEqualTo("/internal/v1/portals")).willReturn(WireMock.notFound()));
		String applicationId = UUID.randomUUID().toString();

		kafka.send(topics.name(Topics.APPLY_REQUESTED), "user-1", envelope(applicationId, 1,
				"https://careers.unmapped-" + UUID.randomUUID() + ".example/jobs/9")).get();

		waitUntil(() -> result(applicationId) != null);
		assertThat(result(applicationId)).contains("\"outcome\":\"NEEDS_YOU\"").contains("No form mapping");
	}

	private void send(String applicationId, int attempt) throws Exception {
		kafka.send(topics.name(Topics.APPLY_REQUESTED), "user-1", envelope(applicationId, attempt, portal.formUrl())).get();
	}

	private String envelope(String applicationId, int attempt, String url) {
		return json.writeValueAsString(new EventEnvelope(UUID.randomUUID().toString(), "ApplyRequested", 1, Instant.now(),
				"test", "user-1", json.valueToTree(new ApplyRequested(applicationId, "user-1", url, attempt,
						Map.of("fullName", "Asha Rao", "email", "asha@example.com")))));
	}

	private String result(String applicationId) {
		List<String> rows = jdbc.queryForList("SELECT payload FROM outbox WHERE topic = ? AND payload LIKE ?", String.class,
				topics.name(Topics.APPLY_COMPLETED), "%" + applicationId + "%");
		return rows.isEmpty() ? null : rows.get(0);
	}

	private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
		Instant deadline = Instant.now().plus(60, ChronoUnit.SECONDS);
		while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
			Thread.sleep(250);
		}
		assertThat(condition.getAsBoolean()).isTrue();
	}

	private static FakePortal startPortal() {
		try {
			return new FakePortal();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
