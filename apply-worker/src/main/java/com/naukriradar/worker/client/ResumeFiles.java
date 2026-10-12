package com.naukriradar.worker.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

import com.naukriradar.worker.config.ServicesProperties;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Puts the resume where the browser can upload it: a file in a fresh temp folder, under the
 * name the user gave it (forms show it). The caller removes the folder when the form is done.
 */
@Component
public class ResumeFiles {

	private final RestClient client;

	public ResumeFiles(RestClient.Builder builder, ServicesProperties properties) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1).connectTimeout(properties.connectTimeout()).build());
		factory.setReadTimeout(properties.readTimeout());
		this.client = builder.clone().requestFactory(factory).baseUrl(properties.coreApiUrl()).build();
	}

	/** The user's resume, downloaded from core-api. */
	public Path download(String userId) {
		return fetch("/internal/v1/users/{userId}/resume/file", userId);
	}

	/** The resume for this application: the one tailored to its job if the user made one, else the original. */
	public Path download(String userId, String applicationId) {
		return fetch("/internal/v1/users/{userId}/applications/{applicationId}/resume/file", userId, applicationId);
	}

	private Path fetch(String uri, Object... variables) {
		return client.get()
				.uri(uri, variables)
				.exchange((request, response) -> {
					if (response.getStatusCode().isError()) {
						throw new IllegalStateException("No resume to attach (core-api answered " + response.getStatusCode().value() + ").");
					}
					String name = fileName(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
					try (InputStream in = response.getBody()) {
						Path file = Files.createTempDirectory("naukriradar-resume-").resolve(name);
						Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
						return file;
					}
				});
	}

	/** A one-page PDF saying it is a test, for dry runs: the real resume never goes into a test form. */
	public Path sample() {
		String text = "Test Candidate - sample resume for a dry run. Not a real application.";
		String stream = "BT /F1 12 Tf 50 750 Td (" + text + ") Tj ET";
		String[] objects = {
			"<< /Type /Catalog /Pages 2 0 R >>",
			"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
			"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
			"<< /Length " + stream.length() + " >>\nstream\n" + stream + "\nendstream",
			"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
		};
		StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
		int[] offsets = new int[objects.length];
		for (int i = 0; i < objects.length; i++) {
			offsets[i] = pdf.length();
			pdf.append(i + 1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");
		}
		int xref = pdf.length();
		pdf.append("xref\n0 ").append(objects.length + 1).append("\n0000000000 65535 f \n");
		for (int offset : offsets) {
			pdf.append(String.format("%010d 00000 n \n", offset));
		}
		pdf.append("trailer\n<< /Size ").append(objects.length + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
		try {
			Path file = Files.createTempDirectory("naukriradar-resume-").resolve("Test-Candidate-Resume.pdf");
			Files.writeString(file, pdf, StandardCharsets.ISO_8859_1);
			return file;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** Deletes the file and the temp folder it was put in. */
	public void remove(Path file) {
		if (file == null) {
			return;
		}
		try (Stream<Path> paths = Files.walk(file.getParent())) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		}
		catch (IOException ignored) {
			// a leftover temp file is harmless; the OS clears its temp folder
		}
	}

	private static String fileName(String disposition) {
		String name = disposition == null ? null : ContentDisposition.parse(disposition).getFilename();
		// keep it a plain file name: no folders, nothing the file system dislikes
		String safe = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|]", "_").strip();
		return safe.isEmpty() ? "Resume.pdf" : safe;
	}

}
