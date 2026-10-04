package com.naukriradar.worker.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.naukriradar.worker.config.BrowserProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Keeps a screenshot of every attempt that went wrong, so a broken selector is easy to see. */
@Component
public class ScreenshotStore {

	private static final Logger log = LoggerFactory.getLogger(ScreenshotStore.class);

	private final Path root;

	public ScreenshotStore(BrowserProperties properties) {
		this.root = properties.screenshots().toAbsolutePath().normalize();
	}

	/** @return the key it was saved under, or null if there was nothing to save or saving failed */
	public String save(String applicationId, int attempt, byte[] png) {
		if (png == null || png.length == 0) {
			return null;
		}
		String key = LocalDate.now(ZoneOffset.UTC) + "/" + applicationId + "-" + attempt + ".png";
		try {
			Path file = root.resolve(key).normalize();
			Files.createDirectories(file.getParent());
			Files.write(file, png);
			return key;
		}
		catch (IOException ex) {
			log.warn("Could not save screenshot for {}: {}", applicationId, ex.getMessage());
			return null;
		}
	}

}
