package com.naukriradar.core.storage;

import java.io.InputStream;

/**
 * Where uploaded files live. Callers deal in keys like {@code resumes/<user>/<id>.pdf}, so
 * moving from local disk to S3 later only needs a new implementation.
 */
public interface FileStorage {

	/** Stores the stream under {@code key}, replacing anything already there. */
	void put(String key, InputStream content);

	/**
	 * Opens the file for reading. The caller must close the stream.
	 * @throws com.naukriradar.core.exception.StoredFileNotFoundException if nothing is stored
	 */
	InputStream open(String key);

	/** Deletes the file if present. Returns false if there was nothing to delete. */
	boolean delete(String key);

}
