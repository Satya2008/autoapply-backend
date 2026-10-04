package com.naukriradar.core.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** What the browser is about to upload; the link is signed for exactly this content type. */
public record DirectUploadRequest(
		@NotBlank @Size(max = 255) String fileName,
		@NotBlank String contentType,
		@Positive long sizeBytes) {
}
