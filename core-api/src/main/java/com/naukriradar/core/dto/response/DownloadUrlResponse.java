package com.naukriradar.core.dto.response;

import java.net.URI;
import java.time.Instant;

public record DownloadUrlResponse(URI url, Instant expiresAt) {
}
