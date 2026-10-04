package com.naukriradar.core.event;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.naukriradar.common.events.EventEnvelope;
import com.naukriradar.common.events.EventTopics;
import com.naukriradar.common.events.Topics;
import com.naukriradar.core.model.Application;
import com.naukriradar.core.model.ApplicationStatus;
import com.naukriradar.core.model.RiskBand;
import com.naukriradar.core.repository.ApplicationRepository;
import com.naukriradar.core.service.ApplyDelayQueue;
import com.naukriradar.core.service.ApplyExecutor;
import com.naukriradar.core.service.ApplyPacingScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Browser mode in core-api: queued applications go to the worker through the paced delay
 * queue and apply.requested, and the worker's results move them on. The worker itself is
 * played by events sent here.
 */
// Fresh consumer groups reading from the latest offset, so no backlog from earlier runs reaches it;
// closed after this class so its consumers don't run on during later classes; "update" so closing
// it doesn't drop the tables the other test contexts still use.
@DirtiesContext
@SpringBootTest(properties = { "spring.kafka.listener.auto-startup=true", "spring.jpa.hibernate.ddl-auto=update",
		"naukriradar.events.group-suffix=.${random.uuid}", "spring.kafka.consumer.auto-offset-reset=latest",
		"naukriradar.applications.mode=BROWSER", "naukriradar.applications.pacing-min=0s",
		"naukriradar.applications.pacing-max=0s" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BrowserModeIT {

	@Autowired
	private ApplicationRepository applications;

	@Autowired
	private ApplyExecutor executor;

	@Autowired
	private ApplyPacingScheduler pacing;

	@Autowired
	private ApplyDelayQueue queue;

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

	@Autowired
	private MockMvc mvc;

	/** The consumers read from the latest offset, so they must be listening before a test sends. */
	@BeforeEach
	void listening() throws InterruptedException {
		waitUntil(() -> listeners.getListenerContainers().stream()
				.allMatch(c -> c.getAssignedPartitions() != null && !c.getAssignedPartitions().isEmpty()));
	}

	@Test
	void aQueuedApplicationIsHandedToTheWorkerAndItsSuccessRecorded() throws Exception {
		Application application = queued();

		executor.process(List.of(application.getId()));

		assertThat(statusOf(application)).isEqualTo(ApplicationStatus.SENDING);
		waitUntil(() -> requested(application.getId()) == 1);
		assertThat(jdbc.queryForObject("SELECT payload FROM outbox WHERE topic = ? AND payload LIKE ?", String.class,
				topics.name(Topics.APPLY_REQUESTED), "%" + application.getId() + "%"))
				.contains("\"attempt\":1").contains("\"fullName\":\"Asha Rao\"");

		// a second run must not hand it over again
		executor.process(List.of(application.getId()));
		pacing.releaseDue();
		assertThat(requested(application.getId())).isEqualTo(1);

		workerSays(application, "SUBMITTED", "Submitted; the portal confirmed it.");
		waitUntil(() -> statusOf(application) == ApplicationStatus.SUBMITTED);
		assertThat(applications.findById(application.getId()).orElseThrow().getSubmittedVia().name()).isEqualTo("BROWSER");
	}

	@Test
	void aFailedAttemptIsRetriedLaterAndAnUnclearOneGoesToTheCandidate() throws Exception {
		Application failing = queued();
		executor.process(List.of(failing.getId()));
		workerSays(failing, "FAILED", "No confirmation within 15s.");
		waitUntil(() -> statusOf(failing) == ApplicationStatus.FAILED);
		assertThat(applications.findById(failing.getId()).orElseThrow().getNextAttemptAt()).isNotNull();

		Application unclear = queued();
		executor.process(List.of(unclear.getId()));
		workerSays(unclear, "UNKNOWN", "The worker stopped in the middle of this attempt.");
		waitUntil(() -> statusOf(unclear) == ApplicationStatus.NEEDS_YOU);
		assertThat(applications.findById(unclear.getId()).orElseThrow().getNeedsYouReason()).contains("stopped in the middle");
	}

	@Test
	void aLateResultForAnApplicationNoLongerWaitingIsIgnored() throws Exception {
		Application application = queued();
		executor.process(List.of(application.getId()));
		workerSays(application, "SUBMITTED", "ok");
		waitUntil(() -> statusOf(application) == ApplicationStatus.SUBMITTED);

		workerSays(application, "FAILED", "late duplicate");
		Thread.sleep(2000);

		assertThat(statusOf(application)).isEqualTo(ApplicationStatus.SUBMITTED);
	}

	@Test
	void theWorkerCanLookUpAPortalByItsHost() throws Exception {
		String domain = "portal-" + UUID.randomUUID().toString().substring(0, 8) + ".example.com";
		mvc.perform(post("/api/v1/admin/portals").contentType(MediaType.APPLICATION_JSON).content("""
				{"domain": "%s", "name": "Example ATS", "riskBand": "LOW", "enabled": true,
				 "selectors": {"fullName": "#name", "submit": "#send"}}""".formatted(domain)))
				.andExpect(status().isCreated());

		mvc.perform(get("/internal/v1/portals").param("domain", "jobs." + domain))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.domain").value(domain))
				.andExpect(jsonPath("$.selectors.fullName").value("#name"));
		mvc.perform(get("/internal/v1/portals").param("domain", domain + ".evil.io")).andExpect(status().isNotFound());
	}

	private Application queued() {
		Application application = new Application(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
				"Backend Engineer", "Acme", "Pune", "https://jobs.lever.co/acme/1", 80, RiskBand.LOW, "ATS",
				json.writeValueAsString(Map.of("fullName", "Asha Rao", "email", "asha@example.com")));
		applications.saveAndFlush(application);
		jdbc.update("UPDATE applications SET status = 'QUEUED' WHERE id = ?", application.getId());
		return applications.findById(application.getId()).orElseThrow();
	}

	private void workerSays(Application application, String outcome, String note) throws Exception {
		Map<String, Object> payload = Map.of("applicationId", application.getId(), "userId", application.getUserId(),
				"attempt", 1, "outcome", outcome, "note", note);
		kafka.send(topics.name(Topics.APPLY_COMPLETED), application.getUserId(), json.writeValueAsString(
				new EventEnvelope(UUID.randomUUID().toString(), "ApplicationAttemptFinished", 1, Instant.now(), "test",
						application.getUserId(), json.valueToTree(payload)))).get();
	}

	private ApplicationStatus statusOf(Application application) {
		return applications.findById(application.getId()).orElseThrow().getStatus();
	}

	private int requested(String applicationId) {
		Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE topic = ? AND payload LIKE ?", Integer.class,
				topics.name(Topics.APPLY_REQUESTED), "%" + applicationId + "%");
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
