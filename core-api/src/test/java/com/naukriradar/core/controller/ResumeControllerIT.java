package com.naukriradar.core.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;
import com.naukriradar.core.support.TestDocuments;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResumeControllerIT {

	private static final String USER_HEADER = "X-User-Id";

	private static final String RESUME_URL = "/api/v1/me/resume";

	/** Matches naukriradar.storage.local-dir in application-test.yml. */
	private static final Path STORAGE = Path.of("build/test-files");

	@Autowired
	private MockMvc mvc;

	@Test
	void uploadFindsSkillsAndLeavesManualSkillsAlone() throws Exception {
		String userId = createUser();
		mvc.perform(put("/api/v1/me/skills").header(USER_HEADER, userId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"skills\": [{\"name\": \"Java\", \"years\": 5}]}"))
				.andExpect(status().isOk());

		upload(userId, pdf("Satya CV.pdf", "Java Developer", "Spring Boot, Docker and JavaScript"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.resume.fileName").value("Satya CV.pdf"))
				.andExpect(jsonPath("$.resume.contentType").value("application/pdf"))
				.andExpect(jsonPath("$.resume.textExtracted").value(true))
				.andExpect(jsonPath("$.skillsFound", containsInAnyOrder("java", "spring boot", "docker", "javascript")))
				.andExpect(jsonPath("$.skillsAdded", not(hasItem("java"))))
				.andExpect(jsonPath("$.autoApplyTurnedOff").value(false));

		mvc.perform(get("/api/v1/me/skills").header(USER_HEADER, userId))
				.andExpect(jsonPath("$[?(@.name == 'java')].source").value("MANUAL"))
				.andExpect(jsonPath("$[?(@.name == 'java')].years").value(5))
				.andExpect(jsonPath("$[?(@.name == 'docker')].source").value("RESUME"));
	}

	@Test
	void downloadReturnsTheSameBytes() throws Exception {
		String userId = createUser();
		byte[] bytes = TestDocuments.docx("Kafka engineer");
		upload(userId, new MockMultipartFile("file", "my résumé.docx", null, bytes)).andExpect(status().isOk());

		byte[] downloaded = mvc.perform(get(RESUME_URL + "/file").header(USER_HEADER, userId))
				.andExpect(status().isOk())
				.andExpect(content().contentType(
						"application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''")))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andReturn().getResponse().getContentAsByteArray();

		assertThat(downloaded).isEqualTo(bytes);
	}

	@Test
	void newUploadReplacesTheOldFileAndItsSkills() throws Exception {
		String userId = createUser();
		upload(userId, pdf("old.pdf", "Angular and Java")).andExpect(status().isOk());

		upload(userId, new MockMultipartFile("file", "new.docx", null, TestDocuments.docx("Java and Kafka")))
				.andExpect(status().isOk());

		mvc.perform(get(RESUME_URL).header(USER_HEADER, userId))
				.andExpect(jsonPath("$.fileName").value("new.docx"));
		mvc.perform(get("/api/v1/me/skills").header(USER_HEADER, userId))
				.andExpect(jsonPath("$[*].name", containsInAnyOrder("java", "kafka")));
		assertThat(storedFiles(userId)).hasSize(1);
	}

	@Test
	void uploadingTheSameFileTwiceKeepsOneCopy() throws Exception {
		String userId = createUser();
		MockMultipartFile file = pdf("cv.pdf", "Java");

		upload(userId, file).andExpect(status().isOk());
		upload(userId, file).andExpect(status().isOk());

		assertThat(storedFiles(userId)).hasSize(1);
	}

	@Test
	void deleteRemovesResumeAndFileButKeepsSkills() throws Exception {
		String userId = createUser();
		upload(userId, pdf("cv.pdf", "Java, Docker, Redis")).andExpect(status().isOk());

		mvc.perform(delete(RESUME_URL).header(USER_HEADER, userId)).andExpect(status().isNoContent());

		mvc.perform(get(RESUME_URL).header(USER_HEADER, userId)).andExpect(status().isNotFound());
		mvc.perform(get(RESUME_URL + "/file").header(USER_HEADER, userId)).andExpect(status().isNotFound());
		mvc.perform(delete(RESUME_URL).header(USER_HEADER, userId)).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/me/skills").header(USER_HEADER, userId))
				.andExpect(jsonPath("$[*].name", hasItems("java", "docker", "redis")));
		assertThat(storedFiles(userId)).isEmpty();
	}

	@Test
	void fileMissingFromStorageIsReportedClearly() throws Exception {
		String userId = createUser();
		upload(userId, pdf("cv.pdf", "Java")).andExpect(status().isOk());
		for (Path stored : storedFiles(userId)) {
			Files.delete(stored);
		}

		mvc.perform(get(RESUME_URL + "/file").header(USER_HEADER, userId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value(containsString("upload it again")));
	}

	@Test
	void executableRenamedToPdfIsRejected() throws Exception {
		String userId = createUser();

		upload(userId, new MockMultipartFile("file", "resume.pdf", "application/pdf", TestDocuments.executable()))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value(containsString("Executable")));
		assertThat(storedFiles(userId)).isEmpty();
	}

	@Test
	void passwordProtectedPdfIsRejected() throws Exception {
		String userId = createUser();

		upload(userId, new MockMultipartFile("file", "cv.pdf", null, TestDocuments.passwordProtectedPdf("Java")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("password")));
	}

	@Test
	void emptyFileIsRejected() throws Exception {
		upload(createUser(), new MockMultipartFile("file", "cv.pdf", "application/pdf", new byte[0]))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("empty")));
	}

	@Test
	void fileOverTenMegabytesIsRejected() throws Exception {
		byte[] tooBig = new byte[10 * 1024 * 1024 + 1];
		System.arraycopy("%PDF-".getBytes(), 0, tooBig, 0, 5);

		upload(createUser(), new MockMultipartFile("file", "big.pdf", "application/pdf", tooBig))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void missingFilePartIsABadRequest() throws Exception {
		mvc.perform(multipart(RESUME_URL).header(USER_HEADER, createUser()))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void nonMultipartBodyIsUnsupported() throws Exception {
		mvc.perform(post(RESUME_URL).header(USER_HEADER, createUser())
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnsupportedMediaType());
	}

	@Test
	void callerMustBeKnown() throws Exception {
		mvc.perform(multipart(RESUME_URL).file(pdf("cv.pdf", "Java")))
				.andExpect(status().isUnauthorized());
		upload(UUID.randomUUID().toString(), pdf("cv.pdf", "Java"))
				.andExpect(status().isNotFound());
	}

	@Test
	void noResumeYetIsNotFound() throws Exception {
		String userId = createUser();

		mvc.perform(get(RESUME_URL).header(USER_HEADER, userId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No resume uploaded yet."));
	}

	private ResultActions upload(String userId, MockMultipartFile file) throws Exception {
		return mvc.perform(multipart(RESUME_URL).file(file).header(USER_HEADER, userId));
	}

	private static MockMultipartFile pdf(String name, String... lines) {
		return new MockMultipartFile("file", name, "application/pdf", TestDocuments.pdf(lines));
	}

	private static List<Path> storedFiles(String userId) throws IOException {
		Path dir = STORAGE.resolve("resumes").resolve(userId);
		if (!Files.exists(dir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.toList();
		}
	}

	private String createUser() throws Exception {
		String body = mvc.perform(post("/api/v1/dev/users").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"user-" + UUID.randomUUID() + "@example.com\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

}
