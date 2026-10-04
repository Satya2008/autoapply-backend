package com.naukriradar.core.event;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import com.naukriradar.common.events.EventEnvelope;
import com.naukriradar.common.events.EventTopics;
import com.naukriradar.common.events.Topics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** match.created -> auto apply -> apply.completed -> the user's live stream, over the local Kafka. */
// Fresh consumer groups reading from the latest offset, so no backlog from earlier runs reaches it;
// closed after this class so its consumers don't run on during later classes; "update" so closing
// it doesn't drop the tables the other test contexts still use.
@DirtiesContext
@SpringBootTest(properties = { "spring.kafka.listener.auto-startup=true", "spring.jpa.hibernate.ddl-auto=update",
		"naukriradar.events.group-suffix=.${random.uuid}", "spring.kafka.consumer.auto-offset-reset=latest" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventFlowIT {

	private static final String USER_HEADER = "X-User-Id";

	@RegisterExtension
	static WireMockExtension matching = WireMockExtension.newInstance().options(wireMockConfig().port(18083)).build();

	@Autowired
	private MockMvc mvc;

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
	void newMatchesStartAnApplyRunForUsersWithAutoApplyAndTheirScreenHearsAboutIt() throws Exception {
		String user = newUser(true);
		matching.stubFor(WireMock.get(urlPathEqualTo("/internal/v1/users/" + user + "/matches")).willReturn(okJson("[]")));
		MvcResult stream = mvc.perform(get("/api/v1/me/applications/stream").header(USER_HEADER, user))
				.andExpect(request().asyncStarted()).andReturn();

		kafka.send(topics.name(Topics.MATCH_CREATED), user, matchCreated(user)).get();

		waitUntil(() -> count("SELECT COUNT(*) FROM apply_runs WHERE user_id = ? AND status = 'SUCCESS'", user) == 1);
		waitUntil(() -> content(stream).contains("apply-run-completed"));
		assertThat(content(stream)).contains("event:connected").contains("\"userId\":\"" + user + "\"");
	}

	@Test
	void usersWithoutAutoApplyAreLeftAlone() throws Exception {
		String user = newUser(false);
		String other = newUser(true);
		matching.stubFor(WireMock.get(urlPathEqualTo("/internal/v1/users/" + other + "/matches")).willReturn(okJson("[]")));

		String event = matchCreated(user);
		kafka.send(topics.name(Topics.MATCH_CREATED), user, event).get();
		kafka.send(topics.name(Topics.MATCH_CREATED), other, matchCreated(other)).get();

		String eventId = JsonPath.read(event, "$.eventId");
		waitUntil(() -> count("SELECT COUNT(*) FROM processed_events WHERE event_id = ?", eventId) == 1);
		waitUntil(() -> count("SELECT COUNT(*) FROM apply_runs WHERE user_id = ?", other) == 1);
		assertThat(count("SELECT COUNT(*) FROM apply_runs WHERE user_id = ?", user)).isZero();
	}

	private String matchCreated(String user) {
		return json.writeValueAsString(new EventEnvelope(UUID.randomUUID().toString(), "MatchRunCompleted", 1, Instant.now(),
				"test", user, json.valueToTree(Map.of("userId", user, "runId", UUID.randomUUID().toString(), "newMatches", 2))));
	}

	private String newUser(boolean autoApply) throws Exception {
		String body = mvc.perform(post("/api/v1/dev/users").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"ev-" + UUID.randomUUID() + "@example.com\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String user = JsonPath.read(body, "$.id");
		mvc.perform(put("/api/v1/me/skills").header(USER_HEADER, user).contentType(MediaType.APPLICATION_JSON)
				.content("{\"skills\": [{\"name\": \"Java\"}, {\"name\": \"SQL\"}, {\"name\": \"Docker\"}]}"))
				.andExpect(status().isOk());
		mvc.perform(put("/api/v1/me/profile").header(USER_HEADER, user).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"fullName": "Satya", "targetRoles": ["Backend Engineer"], "remoteOk": true, "minMatchScore": 50,
						 "dailyApplyLimit": 5, "autoApplyEnabled": %s}""".formatted(autoApply)))
				.andExpect(status().isOk());
		return user;
	}

	private int count(String sql, Object... args) {
		Integer n = jdbc.queryForObject(sql, Integer.class, args);
		return n == null ? 0 : n;
	}

	private static String content(MvcResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
		Instant deadline = Instant.now().plus(30, ChronoUnit.SECONDS);
		while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
			Thread.sleep(200);
		}
		assertThat(condition.getAsBoolean()).isTrue();
	}

}
