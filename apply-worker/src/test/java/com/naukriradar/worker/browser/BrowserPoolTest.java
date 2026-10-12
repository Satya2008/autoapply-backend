package com.naukriradar.worker.browser;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserPoolTest {

	@Test
	void aProfileNoChromeHoldsIsFreeAndItsStaleLockIsCleared(@TempDir Path profile) throws Exception {
		assertThat(BrowserPool.profileInUse(profile)).isFalse();

		// left behind by a Chrome that has exited
		Files.createFile(profile.resolve("lockfile"));

		assertThat(BrowserPool.profileInUse(profile)).isFalse();
		assertThat(profile.resolve("lockfile")).doesNotExist();
	}

}
