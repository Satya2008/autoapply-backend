package com.naukriradar.core.controller;

import com.naukriradar.core.dto.response.StorageMigrationResponse;
import com.naukriradar.core.service.StorageMigrationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/storage")
public class StorageAdminController {

	private final StorageMigrationService migration;

	public StorageAdminController(StorageMigrationService migration) {
		this.migration = migration;
	}

	/** Copies files from local disk into object storage; run once after switching to S3. */
	@PostMapping("/migrate")
	public StorageMigrationResponse migrate() {
		return migration.localToObjectStorage();
	}

}
