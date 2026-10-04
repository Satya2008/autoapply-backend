package com.naukriradar.core.config;

import java.net.URI;

import com.naukriradar.core.storage.FileStorage;
import com.naukriradar.core.storage.LocalFileStorage;
import com.naukriradar.core.storage.S3FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties({ StorageProperties.class, ResumeProperties.class })
public class StorageConfig {

	private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

	@Bean
	FileStorage fileStorage(StorageProperties properties) {
		if (properties.type() == StorageProperties.Type.LOCAL) {
			return new LocalFileStorage(properties.localDir());
		}
		StorageProperties.S3 s3 = properties.s3();
		S3Configuration pathStyle = S3Configuration.builder().pathStyleAccessEnabled(s3.pathStyle()).build();
		var client = S3Client.builder()
				.httpClient(UrlConnectionHttpClient.create())
				.region(Region.of(s3.region()))
				.credentialsProvider(credentials(s3))
				.serviceConfiguration(pathStyle)
				// the SDK's default extra checksums are AWS-only; other S3 stores reject or mangle them
				.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
				.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
		var presigner = S3Presigner.builder()
				.region(Region.of(s3.region()))
				.credentialsProvider(credentials(s3))
				.serviceConfiguration(pathStyle);
		if (s3.endpoint() != null && !s3.endpoint().isBlank()) {
			client.endpointOverride(URI.create(s3.endpoint()));
			presigner.endpointOverride(URI.create(s3.endpoint()));
		}
		S3FileStorage storage = new S3FileStorage(client.build(), presigner.build(), s3.bucket());
		if (s3.createBucket()) {
			storage.ensureBucket();
		}
		log.info("Files are stored in S3 bucket {} at {}", s3.bucket(), s3.endpoint() == null ? "AWS" : s3.endpoint());
		return storage;
	}

	/** Keys from configuration if given, else the usual AWS chain (environment, instance role...). */
	private static AwsCredentialsProvider credentials(StorageProperties.S3 s3) {
		if (s3.accessKey() != null && !s3.accessKey().isBlank()) {
			return StaticCredentialsProvider.create(AwsBasicCredentials.create(s3.accessKey(), s3.secretKey()));
		}
		return DefaultCredentialsProvider.builder().build();
	}

}
