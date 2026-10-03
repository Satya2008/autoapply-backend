package com.naukriradar.core.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

import com.naukriradar.core.exception.StorageException;
import com.naukriradar.core.exception.StoredFileNotFoundException;

/** Keeps files on local disk under a root directory. Good enough for dev and a single box. */
public class LocalFileStorage implements FileStorage {

	private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9/._-]*");

	private final Path root;

	public LocalFileStorage(Path root) {
		this.root = root.toAbsolutePath().normalize();
	}

	@Override
	public void put(String key, InputStream content) {
		Path target = resolve(key);
		Path temp = null;
		try {
			Files.createDirectories(target.getParent());
			// Write next to the target, then move: readers never see a half-written file.
			temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
			Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException ex) {
			deleteQuietly(temp);
			throw new StorageException("Could not store " + key, ex);
		}
	}

	@Override
	public InputStream open(String key) {
		try {
			return Files.newInputStream(resolve(key));
		}
		catch (NoSuchFileException ex) {
			throw new StoredFileNotFoundException(key);
		}
		catch (IOException ex) {
			throw new StorageException("Could not read " + key, ex);
		}
	}

	@Override
	public boolean delete(String key) {
		try {
			return Files.deleteIfExists(resolve(key));
		}
		catch (IOException ex) {
			throw new StorageException("Could not delete " + key, ex);
		}
	}

	/** Maps a key to a path, refusing anything that could escape the root directory. */
	private Path resolve(String key) {
		if (key == null || !SAFE_KEY.matcher(key).matches() || key.contains("..") || key.contains("//")) {
			throw new IllegalArgumentException("Invalid storage key: " + key);
		}
		Path path = root.resolve(key).normalize();
		if (!path.startsWith(root) || path.equals(root)) {
			throw new IllegalArgumentException("Invalid storage key: " + key);
		}
		return path;
	}

	private static void deleteQuietly(Path path) {
		if (path == null) {
			return;
		}
		try {
			Files.deleteIfExists(path);
		}
		catch (IOException ignored) {
			// best effort; the original error matters more
		}
	}

}
