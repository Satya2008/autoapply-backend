package com.naukriradar.core.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
		@NotBlank @Size(max = 72) String currentPassword,
		@NotBlank @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String newPassword) {
}
