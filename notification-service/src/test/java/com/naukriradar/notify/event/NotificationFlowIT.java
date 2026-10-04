package com.naukriradar.notify.event;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jayway.jsonpath.JsonPath;
import com.naukriradar.common.events.EventEnvelope;
import com.naukriradar.common.events.EventTopics;
import com.naukriradar.common.events.Topics;
import jakarta.mail.internet.MimeMessage;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.json.JsonMapper;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** notify.requested -> email (GreenMail SMTP) and Telegram (WireMock), once each. */
// Fresh consumer groups reading from the latest offset, so no backlog from earlier runs reaches it;
// closed after this class so its consumers don't run on during later classes.
@DirtiesContext
@SpringBootTest(properties = { "spring.kafka.listener.auto-startup=true",
		"naukriradar.events.group-suffix=.${random.uuid}", "spring.kafka.consumer.auto-offset-reset=latest" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationFlowIT {

	@RegisterExtension
	static GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP);

	@RegisterExtension
	static WireMockExtension telegram = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@DynamicPropertySource
	static void pointAtFakes(DynamicPropertyRegistry registry) {
		registry.add("naukriradar.notify.telegram-base-url", telegram::baseUrl);
		registry.add("naukriradar.notify.core-api-url", telegram::baseUrl);
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

	@Autowired
	private MockMvc mvc;

	/** The consumers read from the latest offset, so they must be listening before a test sends. */
	@BeforeEach
	void listening() throws InterruptedException {
		telegram.stubFor(WireMock.post(urlEqualTo("/bottest-token/sendMessage")).willReturn(ok("{\"ok\": true}")));
		waitUntil(() -> listeners.getListenerContainers().stream()
				.allMatch(c -> c.getAssignedPartitions() != null && !c.getAssignedPartitions().isEmpty()));
	}

	@Test
	void aDigestGoesOutOnEmailAndTelegramWithTheRealNumbers() throws Exception {
		String email = "asha-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com";
		send(UUID.randomUUID().toString(), digest(email, "7001"));

		waitUntil(() -> mailsTo(email).size() == 1);
		MimeMessage mail = mailsTo(email).getFirst();
		assertThat(mail.getSubject()).isEqualTo("Your day in job hunting: 4 sent, 2 waiting for you");
		assertThat(GreenMailUtil.getBody(mail)).contains("Hi Asha").contains("Backend Engineer at Acme");
		waitUntil(() -> !telegram.findAll(postRequestedFor(urlEqualTo("/bottest-token/sendMessage"))
				.withRequestBody(containing("7001"))).isEmpty());
		assertThat(telegram.findAll(postRequestedFor(urlEqualTo("/bottest-token/sendMessage"))
				.withRequestBody(containing("7001"))).getFirst().getBodyAsString()).contains("4 applications sent for you");
	}

	@Test
	void theSameEventTwiceSendsOneMessage() throws Exception {
		String email = "dup-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com";
		String eventId = UUID.randomUUID().toString();

		send(eventId, digest(email, null));
		send(eventId, digest(email, null));

		waitUntil(() -> mailsTo(email).size() == 1);
		Thread.sleep(1500);
		assertThat(mailsTo(email)).hasSize(1);
	}

	@Test
	void whenSmtpIsDownTheEventWaitsInTheDeadLettersAndAReplaySendsOnlyWhatFailed() throws Exception {
		String email = "down-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com";
		String chat = "7" + (int) (Math.random() * 100_000);
		String eventId = UUID.randomUUID().toString();
		smtp.stop();
		try {
			send(eventId, digest(email, chat));
			waitUntil(() -> count("SELECT COUNT(*) FROM dead_letters WHERE payload LIKE ?", "%" + eventId + "%") == 1);
		}
		finally {
			smtp.start();
		}
		assertThat(logStatus(eventId, "EMAIL")).isEqualTo("FAILED");
		assertThat(logStatus(eventId, "TELEGRAM")).isEqualTo("SENT");

		String letters = mvc.perform(MockMvcRequestBuilders.get("/api/v1/admin/events/dlq")).andReturn().getResponse()
				.getContentAsString();
		String letterId = JsonPath.<List<String>>read(letters, "$[?(@.payload =~ /.*" + eventId + ".*/)].id").getFirst();
		mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/events/dlq/" + letterId + "/replay"))
				.andExpect(status().isOk());

		waitUntil(() -> mailsTo(email).size() == 1);
		assertThat(logStatus(eventId, "EMAIL")).isEqualTo("SENT");
		assertThat(telegram.findAll(postRequestedFor(urlEqualTo("/bottest-token/sendMessage"))
				.withRequestBody(containing(chat)))).hasSize(1);
	}

	@Test
	void anAdminCanSendATestMessage() throws Exception {
		String email = "admin-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com";

		mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/notifications/test").contentType(MediaType.APPLICATION_JSON)
				.content("{\"channel\": \"EMAIL\", \"email\": \"" + email + "\"}"))
				.andExpect(status().isOk());

		assertThat(mailsTo(email)).hasSize(1);
		assertThat(GreenMailUtil.getBody(mailsTo(email).getFirst())).contains("email works");
		mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/notifications/test").contentType(MediaType.APPLICATION_JSON)
				.content("{\"channel\": \"TELEGRAM\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void theBotLinksAChatFromItsCodeAndRefusesCallsWithoutTheSecret() throws Exception {
		telegram.stubFor(WireMock.post(urlEqualTo("/internal/v1/telegram/link")).willReturn(WireMock.noContent()));
		String update = "{\"message\": {\"chat\": {\"id\": 424242}, \"text\": \"/start ABCD2345\"}}";

		mvc.perform(MockMvcRequestBuilders.post("/api/v1/telegram/webhook").contentType(MediaType.APPLICATION_JSON)
				.content(update)).andExpect(status().isNotFound());
		mvc.perform(MockMvcRequestBuilders.post("/api/v1/telegram/webhook").header("X-Telegram-Bot-Api-Secret-Token", "s3cret")
				.contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isOk());

		telegram.verify(postRequestedFor(urlEqualTo("/internal/v1/telegram/link"))
				.withRequestBody(equalToJson("{\"code\": \"ABCD2345\", \"chatId\": \"424242\"}")));
		telegram.verify(postRequestedFor(urlEqualTo("/bottest-token/sendMessage")).withRequestBody(containing("424242"))
				.withRequestBody(containing("NaukriRadar will send your updates here")));
	}

	private Map<String, Object> digest(String email, String chat) {
		Map<String, Object> recipient = new HashMap<>(Map.of("userId", UUID.randomUUID().toString(),
				"name", "Asha", "email", email));
		if (chat != null) {
			recipient.put("telegramChatId", chat);
		}
		return Map.of("template", "daily-digest", "recipient", recipient,
				"channels", chat == null ? List.of("EMAIL") : List.of("EMAIL", "TELEGRAM"),
				"data", Map.of("submitted", 4, "needsYou", 2, "newApplications", 5, "hasWaiting", true,
						"topWaiting", List.of(Map.of("title", "Backend Engineer", "company", "Acme"))));
	}

	private void send(String eventId, Map<String, Object> payload) throws Exception {
		kafka.send(topics.name(Topics.NOTIFY_REQUESTED), "user", json.writeValueAsString(new EventEnvelope(eventId,
				"NotifyRequested", 1, Instant.now(), "test", "user", json.valueToTree(payload)))).get();
	}

	private List<MimeMessage> mailsTo(String email) {
		return Arrays.stream(smtp.getReceivedMessages()).filter(m -> {
			try {
				return m.getAllRecipients()[0].toString().equals(email);
			}
			catch (Exception ex) {
				return false;
			}
		}).toList();
	}

	private String logStatus(String eventId, String channel) {
		List<String> rows = jdbc.queryForList("SELECT status FROM notification_log WHERE event_id = ? AND channel = ?",
				String.class, eventId, channel);
		return rows.isEmpty() ? null : rows.getFirst();
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
