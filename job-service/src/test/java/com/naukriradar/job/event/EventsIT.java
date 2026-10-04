package com.naukriradar.job.event;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.jayway.jsonpath.JsonPath;
import com.naukriradar.common.events.DeadLetters;
import com.naukriradar.common.events.EventEnvelope;
import com.naukriradar.common.events.EventTopics;
import com.naukriradar.common.events.OutboxWriter;
import com.naukriradar.common.events.Topics;
import com.naukriradar.job.dto.response.FetchResultResponse;
import com.naukriradar.job.model.RunStatus;
import com.naukriradar.job.model.RunTrigger;
import com.naukriradar.job.service.FetchRunService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The event plumbing end to end against the local Kafka: outbox, relay, idempotent consumer, dead letters. */
// Fresh consumer groups reading from the latest offset, so no backlog from earlier runs reaches it;
// closed after this class so its consumers don't run on during later classes; "update" so closing
// it doesn't drop the tables the other test contexts still use.
@DirtiesContext
@SpringBootTest(properties = { "spring.kafka.listener.auto-startup=true", "spring.jpa.hibernate.ddl-auto=update",
		"naukriradar.events.group-suffix=.${random.uuid}", "spring.kafka.consumer.auto-offset-reset=latest" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventsIT {

	@Autowired
	private FetchRunService runs;

	@Autowired
	private OutboxWriter outbox;

	@Autowired
	private DeadLetters deadLetters;

	@Autowired
	private EventTopics topics;

	@Autowired
	private KafkaTemplate<String, String> kafka;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JsonMapper json;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private KafkaListenerEndpointRegistry listeners;

	/** The consumers read from the latest offset, so they must be listening before a test sends. */
	@BeforeEach
	void listening() throws InterruptedException {
		waitUntil(() -> listeners.getListenerContainers().stream()
				.allMatch(c -> c.getAssignedPartitions() != null && !c.getAssignedPartitions().isEmpty()));
	}

	@Test
	void aFinishedFetchRunFlowsThroughKafkaToTheParsingConsumer() throws Exception {
		String runId = runs.start(RunTrigger.MANUAL, Instant.now());
		runs.finish(runId, List.of(new FetchResultResponse("board", RunStatus.SUCCESS, 1, 5, 3, 2, 0, 0, "ok", false, 10)),
				Instant.now());

		String eventId = jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.eventId')) FROM outbox"
				+ " WHERE topic = ? AND event_key = ?", String.class, topics.name(Topics.JOBS_INGESTED), runId);
		assertThat(jdbc.queryForObject("SELECT payload FROM outbox WHERE event_key = ?", String.class, runId))
				.contains("\"newJobs\":3").contains("\"updatedJobs\":2");

		// relay -> Kafka -> JobParsingConsumer -> outbox row for jobs.parsed that names this event
		waitUntil(() -> count("SELECT COUNT(*) FROM outbox WHERE topic = ? AND payload LIKE ?",
				topics.name(Topics.JOBS_PARSED), "%" + eventId + "%") == 1);
		assertThat(count("SELECT COUNT(*) FROM outbox WHERE event_key = ? AND published_at IS NOT NULL", runId)).isEqualTo(1);
	}

	@Test
	void anEventCanOnlyBeWrittenTogetherWithTheChangeItDescribes() {
		assertThatThrownBy(() -> outbox.publish(Topics.JOBS_INGESTED, "k", "JobsIngested", Map.of()))
				.isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	void aSecondDeliveryOfTheSameEventIsIgnored() throws Exception {
		String message = envelope(UUID.randomUUID().toString());
		String eventId = JsonPath.read(message, "$.eventId");

		kafka.send(topics.name(Topics.JOBS_INGESTED), "dup", message).get();
		kafka.send(topics.name(Topics.JOBS_INGESTED), "dup", message).get();

		waitUntil(() -> count("SELECT COUNT(*) FROM processed_events WHERE event_id = ?", eventId) == 1);
		Thread.sleep(1500);
		assertThat(count("SELECT COUNT(*) FROM processed_events WHERE event_id = ?", eventId)).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM outbox WHERE topic = ? AND payload LIKE ?", topics.name(Topics.JOBS_PARSED),
				"%" + eventId + "%")).isEqualTo(1);
	}

	@Test
	void aPoisonMessageEndsInTheDeadLettersAndTheNextEventStillGoesThrough() throws Exception {
		String poison = "not an event " + UUID.randomUUID();
		kafka.send(topics.name(Topics.JOBS_INGESTED), "poison", poison).get();
		String next = envelope(UUID.randomUUID().toString());
		kafka.send(topics.name(Topics.JOBS_INGESTED), "poison", next).get();

		waitUntil(() -> count("SELECT COUNT(*) FROM dead_letters WHERE payload = ?", poison) == 1);
		waitUntil(() -> count("SELECT COUNT(*) FROM processed_events WHERE event_id = ?",
				(String) JsonPath.read(next, "$.eventId")) == 1);

		String list = mvc.perform(get("/api/v1/admin/events/dlq").param("topic", topics.name(Topics.JOBS_INGESTED)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(list, "$[?(@.payload == '" + poison + "')].id");
		assertThat(ids).hasSize(1);
		List<String> errors = JsonPath.read(list, "$[?(@.payload == '" + poison + "')].error");
		assertThat(errors.get(0)).isNotBlank();
		mvc.perform(post("/api/v1/admin/events/dlq/" + ids.get(0) + "/replay")).andExpect(status().isBadRequest());
	}

	@Test
	void aDeadLetterCanBeReplayedOnceWithItsOriginalEventId() throws Exception {
		String eventId = UUID.randomUUID().toString();
		deadLetters.record(new ConsumerRecord<>(topics.name(Topics.JOBS_INGESTED), 0, 0L, "k", envelope(eventId)),
				new IllegalStateException("board parser bug"));
		String list = mvc.perform(get("/api/v1/admin/events/dlq")).andReturn().getResponse().getContentAsString();
		String id = JsonPath.<List<String>>read(list, "$[?(@.payload =~ /.*" + eventId + ".*/)].id").get(0);

		mvc.perform(post("/api/v1/admin/events/dlq/" + id + "/replay"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.replayedAt").exists());

		assertThat(count("SELECT COUNT(*) FROM outbox WHERE topic = ? AND payload LIKE ?", topics.name(Topics.JOBS_INGESTED),
				"%" + eventId + "%")).isEqualTo(1);
		waitUntil(() -> count("SELECT COUNT(*) FROM processed_events WHERE event_id = ?", eventId) == 1);
		mvc.perform(post("/api/v1/admin/events/dlq/" + id + "/replay")).andExpect(status().isConflict());
		mvc.perform(post("/api/v1/admin/events/dlq/nope/replay")).andExpect(status().isNotFound());
	}

	@Test
	void outboxStatsShowWhatIsWaiting() throws Exception {
		mvc.perform(get("/api/v1/admin/events/outbox"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pending").isNumber())
				.andExpect(jsonPath("$.sentLastHour").isNumber());
	}

	private String envelope(String eventId) {
		return json.writeValueAsString(new EventEnvelope(eventId, "JobsIngested", 1, Instant.now(), "test", "run-" + eventId,
				json.valueToTree(Map.of("fetchRunId", "run-" + eventId, "newJobs", 0))));
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
