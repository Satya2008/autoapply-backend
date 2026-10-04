package com.naukriradar.core.storage;

import java.net.URI;
import java.time.Duration;
import java.util.OptionalLong;

/**
 * Storage that can hand out time-limited links, so a browser uploads to or downloads from
 * the store directly and the file never passes through our servers. Each link is signed for
 * one key, one method and one content type, and stops working when it expires.
 */
public interface PresignedUrls {

	/** A PUT link; the upload must send exactly this Content-Type. */
	URI uploadUrl(String key, String contentType, Duration validFor);

	/** A GET link that makes the browser save the file under {@code fileName}. */
	URI downloadUrl(String key, String fileName, String contentType, Duration validFor);

	/** Size of a stored object; empty when there is none. */
	OptionalLong size(String key);

}
