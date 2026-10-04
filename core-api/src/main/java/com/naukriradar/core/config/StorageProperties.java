package com.naukriradar.core.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where uploaded files live.
 *
 * @param type LOCAL keeps files on this machine's disk (one instance only); S3 keeps them in
 *     any S3-compatible store (AWS S3, Cloudflare R2, SeaweedFS...) shared by all instances
 * @param localDir where {@code LocalFileStorage} keeps files, and where the migration reads from
 */
@ConfigurationProperties("naukriradar.storage")
public record StorageProperties(
		@DefaultValue("LOCAL") Type type,
		@DefaultValue("./data/files") Path localDir,
		@DefaultValue S3 s3) {

	public enum Type {
		LOCAL, S3
	}

	/**
	 * @param endpoint leave empty for AWS itself; set it for any other S3-compatible store
	 * @param pathStyle bucket in the path ({@code host/bucket/key}); most non-AWS stores need it
	 * @param presignFor how long a presigned upload or download link stays valid
	 */
	public record S3(
			String endpoint,
			@DefaultValue("us-east-1") String region,
			@DefaultValue("naukriradar") String bucket,
			String accessKey,
			String secretKey,
			@DefaultValue("true") boolean pathStyle,
			@DefaultValue("10m") Duration presignFor,
			@DefaultValue("true") boolean createBucket) {
	}

}
