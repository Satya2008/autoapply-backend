package com.naukriradar.core.controller;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.naukriradar.core.config.StorageProperties;
import com.naukriradar.core.storage.FileStorage;
import com.naukriradar.core.storage.PresignedUrls;
import com.naukriradar.core.support.TestDocuments;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Resumes in S3-compatible storage: SeaweedFS's S3 API on localhost:8333 (any S3 store would
 * do; see README for running it).
 */
@SpringBootTest(properties = {
		"naukriradar.storage.type=S3",
		"naukriradar.storage.s3.endpoint=${S3_ENDPOINT:http://127.0.0.1:8333}",
		"naukriradar.storage.s3.bucket=naukriradar-test",
		"naukriradar.storage.s3.access-key=test",
		"naukriradar.storage.s3.secret-key=test" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class S3StorageIT {

	private static final String USER_HEADER = "X-User-Id";

	private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

	@Autowired
	private MockMvc mvc;

	@Autowired
	private FileStorage storage;

	@Autowired
	private StorageProperties properties;

	@Test
	void theBrowserUploadsStraightToStorageAndWeProcessItOnConfirm() throws Exception {
		String user = newUser();
		byte[] pdf = TestDocuments.pdf("Backend developer", "Java, Spring Boot and Docker");

		String link = mvc.perform(post("/api/v1/me/resume/upload-url").header(USER_HEADER, user)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fileName\": \"cv.pdf\", \"contentType\": \"application/pdf\", \"sizeBytes\": " + pdf.length + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.method").value("PUT"))
				.andExpect(jsonPath("$.key", containsString("uploads/" + user + "/")))
				.andReturn().getResponse().getContentAsString();
		String key = JsonPath.read(link, "$.key");

		HttpResponse<String> put = http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(link, "$.uploadUrl")))
				.header("Content-Type", "application/pdf").PUT(HttpRequest.BodyPublishers.ofByteArray(pdf)).build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(put.statusCode()).isEqualTo(200);

		confirm(user, key)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.skillsFound", hasItem("java")))
				.andExpect(jsonPath("$.resume.fileName").value("cv.pdf"));
		assertThat(((PresignedUrls) storage).size(key)).isEmpty();

		String location = mvc.perform(get("/api/v1/me/resume/file").header(USER_HEADER, user))
				.andExpect(status().isFound())
				.andReturn().getResponse().getHeader("Location");
		HttpResponse<byte[]> download = http.send(HttpRequest.newBuilder(URI.create(location)).build(),
				HttpResponse.BodyHandlers.ofByteArray());
		assertThat(download.body()).isEqualTo(pdf);
		assertThat(download.headers().firstValue("Content-Disposition")).hasValueSatisfying(
				v -> assertThat(v).contains("cv.pdf"));
		mvc.perform(get("/api/v1/me/resume/download-url").header(USER_HEADER, user))
				.andExpect(jsonPath("$.url").exists());
	}

	@Test
	void uploadsThatAreNotYoursMissingTooBigOrTheWrongTypeAreRefused() throws Exception {
		String user = newUser();
		String other = newUser();
		mvc.perform(post("/api/v1/me/resume/upload-url").header(USER_HEADER, user).contentType(MediaType.APPLICATION_JSON)
				.content("{\"fileName\": \"x.exe\", \"contentType\": \"application/x-msdownload\", \"sizeBytes\": 10}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/me/resume/upload-url").header(USER_HEADER, user).contentType(MediaType.APPLICATION_JSON)
				.content("{\"fileName\": \"x.pdf\", \"contentType\": \"application/pdf\", \"sizeBytes\": 999999999}"))
				.andExpect(status().isPayloadTooLarge());

		confirm(user, "uploads/" + other + "/" + UUID.randomUUID()).andExpect(status().isNotFound());
		confirm(user, "uploads/" + user + "/" + UUID.randomUUID()).andExpect(status().isNotFound());
		confirm(user, "uploads/" + user + "/../../resumes/x").andExpect(status().isNotFound());
	}

	@Test
	void anExecutableUploadedDirectlyIsRejectedAndRemoved() throws Exception {
		String user = newUser();
		String key = JsonPath.read(mvc.perform(post("/api/v1/me/resume/upload-url").header(USER_HEADER, user)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fileName\": \"cv.pdf\", \"contentType\": \"application/pdf\", \"sizeBytes\": 100}"))
				.andReturn().getResponse().getContentAsString(), "$.key");
		storage.put(key, new ByteArrayInputStream(TestDocuments.executable()));

		confirm(user, key).andExpect(status().isBadRequest());
		assertThat(((PresignedUrls) storage).size(key)).isEmpty();
	}

	@Test
	void localFilesAreCopiedIntoObjectStorageOnceAndRerunsCopyNothing() throws Exception {
		String user = newUser();
		byte[] pdf = TestDocuments.pdf("Migrated resume");
		mvc.perform(multipart("/api/v1/me/resume").file(new MockMultipartFile("file", "old.pdf", "application/pdf", pdf))
				.header(USER_HEADER, user)).andExpect(status().isOk());
		String storageKey = storageKeyOf(user);
		// as if this file had been uploaded before the switch to S3
		storage.delete(storageKey);
		Path local = properties.localDir().resolve(storageKey);
		Files.createDirectories(local.getParent());
		Files.write(local, pdf);

		mvc.perform(post("/api/v1/admin/storage/migrate"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.copied").value(greaterThanOrEqualTo(1)));
		try (InputStream in = storage.open(storageKey)) {
			assertThat(in.readAllBytes()).isEqualTo(pdf);
		}
		mvc.perform(post("/api/v1/admin/storage/migrate")).andExpect(jsonPath("$.copied").value(0));
	}

	private String storageKeyOf(String user) throws Exception {
		String location = mvc.perform(get("/api/v1/me/resume/file").header(USER_HEADER, user))
				.andExpect(header().exists("Location")).andReturn().getResponse().getHeader("Location");
		String path = URI.create(location).getPath();
		return path.substring(path.indexOf("/resumes/") + 1);
	}

	private ResultActions confirm(String user, String key) throws Exception {
		return mvc.perform(post("/api/v1/me/resume/confirm").header(USER_HEADER, user).contentType(MediaType.APPLICATION_JSON)
				.content("{\"key\": \"" + key + "\", \"fileName\": \"cv.pdf\"}"));
	}

	private String newUser() throws Exception {
		String body = mvc.perform(post("/api/v1/dev/users").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"s3-" + UUID.randomUUID() + "@example.com\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

}
