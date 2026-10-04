package com.naukriradar.matching.event;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.naukriradar.common.events.EventEnvelope;
import com.naukriradar.common.events.EventTopics;
import com.naukriradar.common.events.Topics;
import com.naukriradar.matching.service.MatchRunService;
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
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/** jobs.parsed -> rematch the users who use matching -> match.created, over the local Kafka. */
// Fresh consumer groups reading from the latest offset, so no backlog from earlier runs reaches it;
// closed after this class so its consumers don't run on during later classes; "update" so closing
// it doesn't drop the tables the other test contexts still use.
@DirtiesContext
@SpringBootTest(properties = { "spring.kafka.listener.auto-startup=true", "spring.jpa.hibernate.ddl-auto=update",
		"naukriradar.events.group-suffix=.${random.uuid}", "spring.kafka.consumer.auto-offset-reset=latest" })
@ActiveProfiles("test")
class RematchIT {

	@RegisterExtension
	static WireMockExtension services = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@DynamicPropertySource
	static void pointAtWireMock(DynamicPropertyRegistry registry) {
		registry.add("naukriradar.services.core-api-url", services::baseUrl);
		registry.add("naukriradar.services.job-service-url", services::baseUrl);
	}

	@Autowired
	private MatchRunService runs;

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

	/** The consumers read from the latest offset, so they must be listening before a test sends. */
	@BeforeEach
	void listening() throws InterruptedException {
		waitUntil(() -> listeners.getListenerContainers().stream()
				.allMatch(c -> c.getAssignedPartitions() != null && !c.getAssignedPartitions().isEmpty()));
	}

	@Test
	void newlyParsedJobsRematchActiveUsersAndEachRunIsAnnounced() throws Exception {
		String user = UUID.randomUUID().toString();
		services.stubFor(WireMock.get(urlEqualTo("/internal/v1/users/" + user + "/matching-profile")).willReturn(okJson("""
				{"userId": "%s", "skills": ["java"], "targetRoles": ["Backend Engineer"], "preferredLocations": [],
				 "remoteOk": true, "excludedCompanies": [], "excludedKeywords": [], "minMatchScore": 40}
				""".formatted(user))));
		services.stubFor(WireMock.post(urlEqualTo("/internal/v1/jobs/candidates")).willReturn(okJson("[]")));
		runs.start(user);
		waitUntil(() -> count("SELECT COUNT(*) FROM match_runs WHERE user_id = ? AND status = 'SUCCESS'", user) == 1);

		String event = json.writeValueAsString(new EventEnvelope(UUID.randomUUID().toString(), "JobsParsed", 1,
				Instant.now(), "test", "round-1", json.valueToTree(Map.of("parsedJobs", 3))));
		kafka.send(topics.name(Topics.JOBS_PARSED), "round-1", event).get();

		waitUntil(() -> count("SELECT COUNT(*) FROM match_runs WHERE user_id = ? AND status = 'SUCCESS'", user) == 2);
		assertThat(count("SELECT COUNT(*) FROM outbox WHERE topic = ? AND event_key = ?", topics.name(Topics.MATCH_CREATED),
				user)).isEqualTo(2);
	}

	private int count(String sql, Object... args) {
		Integer n = jdbc.queryForObject(sql, Integer.class, args);
		return n == null ? 0 : n;
	}

	private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
		Instant deadline = Instant.now().plus(30, ChronoUnit.SECONDS);
		while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
			Thread.sleep(200);
		}
		assertThat(condition.getAsBoolean()).isTrue();
	}

}
