package com.naukriradar.core.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.naukriradar.core.exception.StoredFileNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

	@TempDir
	Path root;

	@Test
	void storesReadsAndDeletes() throws IOException {
		LocalFileStorage storage = new LocalFileStorage(root);

		storage.put("resumes/u1/a.pdf", stream("hello"));

		try (InputStream in = storage.open("resumes/u1/a.pdf")) {
			assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
		}
		assertThat(storage.delete("resumes/u1/a.pdf")).isTrue();
		assertThat(storage.delete("resumes/u1/a.pdf")).isFalse();
	}

	@Test
	void putReplacesAndLeavesNoTempFiles() throws IOException {
		LocalFileStorage storage = new LocalFileStorage(root);

		storage.put("k/file.pdf", stream("first"));
		storage.put("k/file.pdf", stream("second"));

		assertThat(Files.readString(root.resolve("k/file.pdf"))).isEqualTo("second");
		try (var files = Files.list(root.resolve("k"))) {
			assertThat(files).hasSize(1);
		}
	}

	@Test
	void missingFileHasItsOwnException() {
		LocalFileStorage storage = new LocalFileStorage(root);

		assertThatThrownBy(() -> storage.open("nope.pdf")).isInstanceOf(StoredFileNotFoundException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "../escape.pdf", "a/../../escape.pdf", "/etc/passwd", "C:\\Windows\\x", "a//b", "", " a" })
	void keysCannotEscapeTheRoot(String key) {
		LocalFileStorage storage = new LocalFileStorage(root);

		assertThatThrownBy(() -> storage.put(key, stream("x"))).isInstanceOf(IllegalArgumentException.class);
	}

	private static InputStream stream(String text) {
		return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
	}

}
