package com.naukriradar.core.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param key the key the upload link was made for */
public record ConfirmUploadRequest(@NotBlank @Size(max = 500) String key, @Size(max = 255) String fileName) {
}
