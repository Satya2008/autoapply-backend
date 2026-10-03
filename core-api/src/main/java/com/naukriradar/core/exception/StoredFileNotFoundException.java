package com.naukriradar.core.exception;

/** The database points at a file that is no longer in storage. */
public class StoredFileNotFoundException extends StorageException {

	public StoredFileNotFoundException(String key) {
		super("No stored file for key " + key);
	}

}
