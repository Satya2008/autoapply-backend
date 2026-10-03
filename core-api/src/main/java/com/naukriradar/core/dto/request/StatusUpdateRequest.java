package com.naukriradar.core.dto.request;

import com.naukriradar.core.model.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What the employer said: INTERVIEW, OFFER or REJECTED. */
public record StatusUpdateRequest(
		@NotNull ApplicationStatus status,
		@Size(max = 300) String note) {
}
