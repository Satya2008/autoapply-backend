package com.naukriradar.core.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.naukriradar.core.service.RiskClassifier;
import com.naukriradar.core.settings.SettingDefinitions;
import com.naukriradar.core.settings.Settings;
import org.junit.jupiter.api.AfterEach;
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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runtime settings, encrypted secrets, the audit trail and the job admin API, end to end. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSettingsAuditIT {

	private static final String SETTINGS = "/api/v1/admin/settings";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private Settings settings;

	@Autowired
	private RiskClassifier riskClassifier;

	@Autowired
	private JdbcTemplate jdbc;

	/** Settings are shared by the whole test context; put every one back. */
	@AfterEach
	void resetAll() throws Exception {
		for (var definition : SettingDefinitions.all()) {
			mvc.perform(post(SETTINGS + "/" + definition.key() + "/reset"));
		}
	}

	@Test
	void aChangedSettingIsUsedOnTheVeryNextRead() throws Exception {
		assertThat(settings.getInt(SettingDefinitions.MAX_ATTEMPTS)).isEqualTo(3);

		update(SettingDefinitions.MAX_ATTEMPTS, "5").andExpect(status().isOk())
				.andExpect(jsonPath("$.value").value("5"))
				.andExpect(jsonPath("$.defaultValue").value("3"))
				.andExpect(jsonPath("$.overridden").value(true))
				.andExpect(jsonPath("$.updatedBy").value("admin-1"));

		assertThat(settings.getInt(SettingDefinitions.MAX_ATTEMPTS)).isEqualTo(5);

		mvc.perform(post(SETTINGS + "/" + SettingDefinitions.MAX_ATTEMPTS + "/reset"))
				.andExpect(jsonPath("$.value").value("3"))
				.andExpect(jsonPath("$.overridden").value(false));
		assertThat(settings.getInt(SettingDefinitions.MAX_ATTEMPTS)).isEqualTo(3);
	}

	@Test
	void movingASiteBetweenRiskListsTakesEffectImmediately() throws Exception {
		assertThat(riskClassifier.classify("https://jobs.example-ats.com/1", List.of()).band().name()).isEqualTo("MEDIUM");

		update(SettingDefinitions.LOW_RISK_DOMAINS, "greenhouse.io, Example-ATS.com").andExpect(status().isOk())
				.andExpect(jsonPath("$.value").value("greenhouse.io, example-ats.com"));

		assertThat(riskClassifier.classify("https://jobs.example-ats.com/1", List.of()).band().name()).isEqualTo("LOW");
	}

	@Test
	void secretsAreEncryptedAtRestAndNeverShown() throws Exception {
		update(SettingDefinitions.AI_API_KEY, "sk-very-secret-123").andExpect(status().isOk())
				.andExpect(jsonPath("$.value").value("****"))
				.andExpect(jsonPath("$.defaultValue").value(nullValue()));

		String stored = jdbc.queryForObject("SELECT setting_value FROM settings WHERE setting_key = ?", String.class,
				SettingDefinitions.AI_API_KEY);
		assertThat(stored).startsWith("v1:").doesNotContain("sk-very-secret");
		assertThat(settings.getString(SettingDefinitions.AI_API_KEY)).isEqualTo("sk-very-secret-123");
		mvc.perform(get(SETTINGS).param("category", "ai"))
				.andExpect(jsonPath("$[0].value").value("****"));

		String detail = waitForAudit("SETTING_UPDATE", SettingDefinitions.AI_API_KEY).get(0);
		assertThat(detail).isEqualTo("secret value changed").doesNotContain("sk-very");
	}

	@Test
	void badValuesAndUnknownKeysAreRejected() throws Exception {
		update(SettingDefinitions.MAX_ATTEMPTS, "0").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("at least 1")));
		update(SettingDefinitions.AUTO_APPLY_CRON, "every morning").andExpect(status().isBadRequest());
		update(SettingDefinitions.HIGH_RISK_DOMAINS, "linkedin.com, http://x").andExpect(status().isBadRequest());
		update("no.such.setting", "1").andExpect(status().isNotFound());
		mvc.perform(put(SETTINGS + "/" + SettingDefinitions.MAX_ATTEMPTS).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());

		assertThat(settings.getInt(SettingDefinitions.MAX_ATTEMPTS)).isEqualTo(3);
	}

	@Test
	void listCanBeFilteredByCategory() throws Exception {
		mvc.perform(get(SETTINGS).param("category", "scheduler"))
				.andExpect(jsonPath("$", hasSize(4)))
				.andExpect(jsonPath("$[*].category", not(hasItem("applications"))));
		mvc.perform(get(SETTINGS)).andExpect(jsonPath("$", hasSize(SettingDefinitions.all().size())));
	}

	@Test
	void adminActionsAreAuditedWithWhoAndWhereEvenWhenTheyFail() throws Exception {
		String domain = "audit-" + UUID.randomUUID().toString().substring(0, 8) + ".example.com";
		String body = "{\"domain\": \"" + domain + "\", \"name\": \"Audit test\", \"riskBand\": \"LOW\", \"enabled\": true}";
		String portalId = JsonPath.read(mvc.perform(post("/api/v1/admin/portals").contentType(MediaType.APPLICATION_JSON)
				.content(body).header("X-User-Id", "admin-7").header("X-Forwarded-For", "203.0.113.9, 10.0.0.1"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(post("/api/v1/admin/portals").contentType(MediaType.APPLICATION_JSON).content(body)
				.header("X-User-Id", "admin-7"))
				.andExpect(status().isConflict());

		waitForAudit("PORTAL_CREATE", portalId);
		String page = mvc.perform(get("/api/v1/admin/audit").param("actor", "admin-7").param("action", "PORTAL_CREATE"))
				.andReturn().getResponse().getContentAsString();
		List<Boolean> outcomes = JsonPath.read(page, "$.items[*].success");
		assertThat(outcomes).contains(true, false);
		List<String> ips = JsonPath.read(page, "$.items[?(@.success == true)].ip");
		List<String> details = JsonPath.read(page, "$.items[?(@.success == true)].detail");
		List<String> errors = JsonPath.read(page, "$.items[?(@.success == false)].error");
		assertThat(ips).containsExactly("203.0.113.9");
		assertThat(details).containsExactly(domain + " set to LOW");
		assertThat(errors).isNotEmpty().allMatch(e -> e.contains("already exists"));

		mvc.perform(get("/api/v1/admin/audit").param("cursor", "nope")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/admin/audit").param("limit", "0")).andExpect(status().isBadRequest());
	}

	@Test
	void jobsAreListedAndCanBeRunByHand() throws Exception {
		mvc.perform(get("/api/v1/admin/scheduler"))
				.andExpect(jsonPath("$[*].name", containsInAnyOrder("auto-apply", "retry-failed")))
				.andExpect(jsonPath("$[0].scheduled").value(false));

		mvc.perform(post("/api/v1/admin/scheduler/retry-failed/run").header("X-User-Id", "admin-9"))
				.andExpect(status().isAccepted());
		Instant deadline = Instant.now().plus(10, ChronoUnit.SECONDS);
		String result = null;
		while (result == null && Instant.now().isBefore(deadline)) {
			List<String> results = JsonPath.read(mvc.perform(get("/api/v1/admin/scheduler")).andReturn().getResponse()
					.getContentAsString(), "$[?(@.name == 'retry-failed')].lastResult");
			result = results.isEmpty() ? null : results.get(0);
			Thread.sleep(100);
		}
		assertThat(result).startsWith("Retried for");
		waitForAudit("JOB_RUN", "retry-failed");

		mvc.perform(post("/api/v1/admin/scheduler/nope/run")).andExpect(status().isNotFound());
	}

	private ResultActions update(String key, String value) throws Exception {
		return mvc.perform(put(SETTINGS + "/" + key).header("X-User-Id", "admin-1").contentType(MediaType.APPLICATION_JSON)
				.content("{\"value\": \"" + value + "\"}"));
	}

	/** Audit entries are written asynchronously; wait for one and return the details of all matching. */
	private List<String> waitForAudit(String action, String targetId) throws InterruptedException {
		for (int i = 0; i < 100; i++) {
			List<String> details = jdbc.queryForList(
					"SELECT COALESCE(detail, '') FROM audit_log WHERE action = ? AND target_id = ? ORDER BY id DESC",
					String.class, action, targetId);
			if (!details.isEmpty()) {
				return details;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("No audit entry for " + action + " " + targetId);
	}

}
