package com.naukriradar.core.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import com.naukriradar.core.model.Application;
import com.naukriradar.core.model.RiskBand;
import com.naukriradar.core.repository.ApplicationRepository;
import com.naukriradar.core.support.TestDocuments;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Resume parsing and cover letters, with WireMock on port 18083 playing matching-service's AI. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiFeaturesIT {

	private static final String USER_HEADER = "X-User-Id";

	@RegisterExtension
	static WireMockExtension matching = WireMockExtension.newInstance().options(wireMockConfig().port(18083)).build();

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ApplicationRepository applications;

	@Test
	void aiReadsTheUploadedResumeAndAddsSkillsWithYears() throws Exception {
		matching.stubFor(post(urlPathEqualTo("/internal/v1/ai/run"))
				.withRequestBody(equalToJson("{\"prompt\": \"resume-parse\"}", true, true))
				.willReturn(okJson("""
						{"provider": "fake", "model": "m", "answer": {
						  "skills": [{"name": "Java", "years": 4}, {"name": "Kubernetes", "years": 1}, {"name": "Go"}],
						  "seniority": "mid", "totalYearsExperience": 4, "roles": ["Backend Developer"]}}""")));
		String user = newUser();
		MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", MediaType.APPLICATION_PDF_VALUE,
				TestDocuments.pdf("Backend developer", "Java and Spring Boot for four years"));

		mvc.perform(multipart("/api/v1/me/resume").file(file).header(USER_HEADER, user)).andExpect(status().isOk());

		String skills = waitFor(() -> mvc.perform(get("/api/v1/me/skills").header(USER_HEADER, user)).andReturn()
				.getResponse().getContentAsString(), s -> s.contains("kubernetes"));
		assertThat(JsonPath.<List<Integer>>read(skills, "$[?(@.name == 'kubernetes')].years")).containsExactly(1);
		assertThat(JsonPath.<List<String>>read(skills, "$[*].name")).contains("java", "go", "spring boot");
		mvc.perform(get("/api/v1/me/resume").header(USER_HEADER, user))
				.andExpect(jsonPath("$.aiParsed.seniority").value("mid"));
		mvc.perform(get("/api/v1/me/profile").header(USER_HEADER, user))
				.andExpect(jsonPath("$.experienceYears").value(4));

		mvc.perform(MockMvcRequestBuilders.post("/api/v1/me/resume/parse").header(USER_HEADER, user))
				.andExpect(status().isAccepted());
		mvc.perform(MockMvcRequestBuilders.post("/api/v1/me/resume/parse").header(USER_HEADER, newUser()))
				.andExpect(status().isNotFound());
	}

	@Test
	void aCoverLetterIsWrittenForAnApplicationAndCanBeRegenerated() throws Exception {
		matching.stubFor(post(urlPathEqualTo("/internal/v1/ai/run"))
				.withRequestBody(equalToJson("{\"prompt\": \"cover-letter\"}", true, true))
				.willReturn(okJson("""
						{"provider": "fake", "model": "strong", "answer": {"letter": "Dear hiring team, ..."}}""")));
		String user = newUser();
		mvc.perform(MockMvcRequestBuilders.put("/api/v1/me/skills").header(USER_HEADER, user)
				.contentType(MediaType.APPLICATION_JSON).content("{\"skills\": [{\"name\": \"java\", \"years\": 3}]}"));
		Application application = applications.saveAndFlush(new Application(user, UUID.randomUUID().toString(),
				"Backend Engineer", "Acme", "Pune", "https://jobs.example.com/1", 80, RiskBand.MEDIUM, "unknown site", "{}"));

		mvc.perform(MockMvcRequestBuilders.post("/api/v1/me/applications/" + application.getId() + "/cover-letter")
				.header(USER_HEADER, user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.letter").value("Dear hiring team, ..."))
				.andExpect(jsonPath("$.writtenBy").value("fake:strong"));
		mvc.perform(MockMvcRequestBuilders.post("/api/v1/me/applications/" + application.getId() + "/cover-letter")
				.param("regenerate", "true").header(USER_HEADER, user))
				.andExpect(status().isOk());
		mvc.perform(get("/api/v1/me/applications/" + application.getId()).header(USER_HEADER, user))
				.andExpect(jsonPath("$.coverLetter").value("Dear hiring team, ..."));

		String sent = matching.findAll(postRequestedFor(urlPathEqualTo("/internal/v1/ai/run"))
				.withRequestBody(equalToJson("{\"prompt\": \"cover-letter\"}", true, true))).getLast().getBodyAsString();
		assertThat(sent).contains("Backend Engineer").contains("\"fresh\":true").contains(user);

		mvc.perform(MockMvcRequestBuilders.post("/api/v1/me/applications/" + application.getId() + "/cover-letter")
				.header(USER_HEADER, newUser()))
				.andExpect(status().isNotFound());
	}

	@Test
	void withoutAiTheCoverLetterAnswerSaysWhy() throws Exception {
		matching.stubFor(post(urlPathEqualTo("/internal/v1/ai/run")).willReturn(okJson(
				"{\"answer\": null, \"unavailableReason\": \"Today's AI budget is used up.\"}")));
		String user = newUser();
		Application application = applications.saveAndFlush(new Application(user, UUID.randomUUID().toString(),
				"QA", "Initech", null, "https://jobs.example.com/2", 70, RiskBand.MEDIUM, "unknown site", "{}"));

		mvc.perform(MockMvcRequestBuilders.post("/api/v1/me/applications/" + application.getId() + "/cover-letter")
				.header(USER_HEADER, user))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.detail", containsString("budget")));
	}

	private String newUser() throws Exception {
		String body = mvc.perform(MockMvcRequestBuilders.post("/api/v1/dev/users").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"ai-" + UUID.randomUUID() + "@example.com\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private static String waitFor(ThrowingSupplier read, Predicate<String> done) throws Exception {
		Instant deadline = Instant.now().plus(10, ChronoUnit.SECONDS);
		String value = read.get();
		while (!done.test(value) && Instant.now().isBefore(deadline)) {
			Thread.sleep(100);
			value = read.get();
		}
		return value;
	}

	private interface ThrowingSupplier {

		String get() throws Exception;

	}

}
