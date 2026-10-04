package com.naukriradar.core.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.OptionalLong;

import com.naukriradar.core.exception.StorageException;
import com.naukriradar.core.exception.StoredFileNotFoundException;
import org.springframework.http.ContentDisposition;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Files in an S3-compatible store. Every instance sees the same files, and nothing is lost
 * when a container is replaced, which is what local disk can't offer.
 */
public class S3FileStorage implements FileStorage, PresignedUrls {

	private final S3Client s3;
	private final S3Presigner presigner;
	private final String bucket;

	public S3FileStorage(S3Client s3, S3Presigner presigner, String bucket) {
		this.s3 = s3;
		this.presigner = presigner;
		this.bucket = bucket;
	}

	/** Creates the bucket if it isn't there yet (for local and test setups). */
	public void ensureBucket() {
		try {
			s3.headBucket(b -> b.bucket(bucket));
		}
		catch (S3Exception ex) {
			if (ex.statusCode() != 404) {
				throw new StorageException("Can't reach bucket " + bucket, ex);
			}
			s3.createBucket(b -> b.bucket(bucket));
		}
	}

	/** Resumes are at most a few MB, so the stream is read into memory to know its length. */
	@Override
	public void put(String key, InputStream content) {
		try {
			byte[] bytes = content.readAllBytes();
			s3.putObject(p -> p.bucket(bucket).key(key), RequestBody.fromBytes(bytes));
		}
		catch (IOException | SdkException ex) {
			throw new StorageException("Could not store " + key, ex);
		}
	}

	@Override
	public InputStream open(String key) {
		try {
			return s3.getObject(g -> g.bucket(bucket).key(key));
		}
		catch (NoSuchKeyException ex) {
			throw new StoredFileNotFoundException(key);
		}
		catch (SdkException ex) {
			throw new StorageException("Could not read " + key, ex);
		}
	}

	@Override
	public boolean delete(String key) {
		try {
			if (size(key).isEmpty()) {
				return false;
			}
			s3.deleteObject(d -> d.bucket(bucket).key(key));
			return true;
		}
		catch (SdkException ex) {
			throw new StorageException("Could not delete " + key, ex);
		}
	}

	@Override
	public OptionalLong size(String key) {
		try {
			return OptionalLong.of(s3.headObject(h -> h.bucket(bucket).key(key)).contentLength());
		}
		catch (NoSuchKeyException ex) {
			return OptionalLong.empty();
		}
		catch (S3Exception ex) {
			if (ex.statusCode() == 404) {
				return OptionalLong.empty();
			}
			throw new StorageException("Could not check " + key, ex);
		}
	}

	@Override
	public URI uploadUrl(String key, String contentType, Duration validFor) {
		return URI.create(presigner.presignPutObject(p -> p.signatureDuration(validFor)
				.putObjectRequest(o -> o.bucket(bucket).key(key).contentType(contentType)))
				.url().toString());
	}

	@Override
	public URI downloadUrl(String key, String fileName, String contentType, Duration validFor) {
		String disposition = ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString();
		return URI.create(presigner.presignGetObject(p -> p.signatureDuration(validFor)
				.getObjectRequest(o -> o.bucket(bucket).key(key).responseContentType(contentType)
						.responseContentDisposition(disposition)))
				.url().toString());
	}

}
