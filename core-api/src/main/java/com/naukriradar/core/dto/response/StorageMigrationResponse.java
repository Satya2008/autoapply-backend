package com.naukriradar.core.dto.response;

/**
 * @param copied files copied from local disk to object storage now
 * @param alreadyThere files object storage already had (a rerun copies nothing twice)
 * @param missingLocally files the database knows but neither place has
 */
public record StorageMigrationResponse(int files, int copied, int alreadyThere, int missingLocally) {
}
