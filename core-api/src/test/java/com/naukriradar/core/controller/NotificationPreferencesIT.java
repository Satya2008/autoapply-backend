package com.naukriradar.core.controller;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.naukriradar.common.events.EventTopics;
import com.naukriradar.common.events.Topics;
import com.naukriradar.core.model.Application;
import com.naukriradar.core.model.RiskBand;
import com.naukriradar.core.repository.ApplicationRepository;
import com.naukriradar.core.scheduler.DailyDigestJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Notification preferences, Telegram linking and the daily digest's numbers. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationPreferencesIT {

	private static final String USER_HEADER = "X-User-Id";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private DailyDigestJob digest;

	@Autowired
	private ApplicationRepository applications;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EventTopics topics;

	@Test
	void preferencesStartWithEmailOnAndTelegramNeedsLinkingFirst() throws Exception {
		String user = newUser();

		mvc.perform(get("/api/v1/me/notification-preferences").header(USER_HEADER, user))
				.andExpect(jsonPath("$.emailEnabled").value(true))
				.andExpect(jsonPath("$.telegramEnabled").value(false))
				.andExpect(jsonPath("$.telegramLinked").value(false))
				.andExpect(jsonPath("$.digestEnabled").value(true));
		update(user, true, true, true).andExpect(status().isUnprocessableContent());

		String link = mvc.perform(post("/api/v1/me/telegram/link").header(USER_HEADER, user))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String code = JsonPath.read(link, "$.code");
		assertThat(code).hasSize(8).doesNotContain("0", "O", "1", "I");
		assertThat((String) JsonPath.read(link, "$.instructions")).contains("/start " + code);

		mvc.perform(post("/internal/v1/telegram/link").contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\": \"" + code.toLowerCase() + "\", \"chatId\": \"424242\"}"))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/me/notification-preferences").header(USER_HEADER, user))
				.andExpect(jsonPath("$.telegramLinked").value(true))
				.andExpect(jsonPath("$.telegramEnabled").value(true));

		// a code works once
		mvc.perform(post("/internal/v1/telegram/link").contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\": \"" + code + "\", \"chatId\": \"999\"}"))
				.andExpect(status().isNotFound());
		update(user, false, true, false).andExpect(jsonPath("$.emailEnabled").value(false))
				.andExpect(jsonPath("$.digestEnabled").value(false));
	}

	@Test
	void theDigestCarriesRealNumbersAndSkipsUsersWithNothingNew() throws Exception {
		String busy = newUser();
		String quiet = newUser();
		application(busy, "SIMULATED", 70);
		application(busy, "NEEDS_YOU", 90);
		application(busy, "NEEDS_YOU", 60);

		digest.run();

		List<String> busyEvents = notifyEvents(busy);
		assertThat(busyEvents).hasSize(1);
		String payload = busyEvents.getFirst();
		assertThat((Integer) JsonPath.read(payload, "$.payload.data.submitted")).isEqualTo(1);
		assertThat((Integer) JsonPath.read(payload, "$.payload.data.needsYou")).isEqualTo(2);
		assertThat((Integer) JsonPath.read(payload, "$.payload.data.newApplications")).isEqualTo(3);
		assertThat((String) JsonPath.read(payload, "$.payload.data.topWaiting[0].title")).isEqualTo("Job 90");
		assertThat((String) JsonPath.read(payload, "$.payload.template")).isEqualTo("daily-digest");
		assertThat((List<String>) JsonPath.read(payload, "$.payload.channels")).containsExactly("EMAIL");
		assertThat(notifyEvents(quiet)).isEmpty();
	}

	private void application(String user, String status, int score) {
		Application application = applications.saveAndFlush(new Application(user, UUID.randomUUID().toString(),
				"Job " + score, "Acme", "Pune", "https://jobs.lever.co/acme/" + score, score, RiskBand.MEDIUM, "test", "{}"));
		jdbc.update("UPDATE applications SET status = ? WHERE id = ?", status, application.getId());
	}

	private List<String> notifyEvents(String user) {
		return jdbc.queryForList("SELECT payload FROM outbox WHERE topic = ? AND event_key = ?", String.class,
				topics.name(Topics.NOTIFY_REQUESTED), user);
	}

	private ResultActions update(String user, boolean email, boolean telegram, boolean digest) throws Exception {
		return mvc.perform(put("/api/v1/me/notification-preferences").header(USER_HEADER, user)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"emailEnabled": %s, "telegramEnabled": %s, "digestEnabled": %s, "applyUpdates": true}"""
						.formatted(email, telegram, digest)));
	}

	private String newUser() throws Exception {
		String body = mvc.perform(post("/api/v1/dev/users").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"n-" + UUID.randomUUID() + "@example.com\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

}
