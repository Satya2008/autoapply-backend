package com.naukriradar.core.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.naukriradar.common.exception.BadRequestException;
import com.naukriradar.core.audit.Audited;
import com.naukriradar.core.config.StorageProperties;
import com.naukriradar.core.dto.response.StorageMigrationResponse;
import com.naukriradar.core.exception.StoredFileNotFoundException;
import com.naukriradar.core.model.Resume;
import com.naukriradar.core.repository.ResumeRepository;
import com.naukriradar.core.storage.FileStorage;
import com.naukriradar.core.storage.LocalFileStorage;
import com.naukriradar.core.storage.PresignedUrls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Moves files from local disk into object storage after switching {@code storage.type} to
 * S3. Keys stay the same, so the database needs no change. Safe to run again: a file already
 * in object storage is skipped, and the local copy is never deleted.
 */
@Service
public class StorageMigrationService {

	private static final Logger log = LoggerFactory.getLogger(StorageMigrationService.class);

	private final ResumeRepository resumes;
	private final FileStorage storage;
	private final StorageProperties properties;

	public StorageMigrationService(ResumeRepository resumes, FileStorage storage, StorageProperties properties) {
		this.resumes = resumes;
		this.storage = storage;
		this.properties = properties;
	}

	@Audited(action = "STORAGE_MIGRATE", targetType = "storage", targetId = "'local-to-s3'")
	public StorageMigrationResponse localToObjectStorage() {
		if (!(storage instanceof PresignedUrls target)) {
			throw new BadRequestException("Storage is local already; set naukriradar.storage.type=S3 first.");
		}
		LocalFileStorage local = new LocalFileStorage(properties.localDir());
		List<String> keys = keys();
		int copied = 0;
		int alreadyThere = 0;
		int missing = 0;
		for (String key : keys) {
			if (target.size(key).isPresent()) {
				alreadyThere++;
				continue;
			}
			try (InputStream in = local.open(key)) {
				storage.put(key, in);
				copied++;
			}
			catch (StoredFileNotFoundException ex) {
				log.warn("Resume file {} is in neither place", key);
				missing++;
			}
			catch (IOException ex) {
				log.warn("Could not copy {}", key, ex);
				missing++;
			}
		}
		log.info("Storage migration: {} files, {} copied, {} already there, {} missing", keys.size(), copied, alreadyThere,
				missing);
		return new StorageMigrationResponse(keys.size(), copied, alreadyThere, missing);
	}

	private List<String> keys() {
		return resumes.findAll().stream().map(Resume::getStorageKey).toList();
	}

}
