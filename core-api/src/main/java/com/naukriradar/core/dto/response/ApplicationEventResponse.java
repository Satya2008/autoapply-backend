package com.naukriradar.core.dto.response;

import java.time.Instant;

import com.naukriradar.core.model.ApplicationStatus;

public record ApplicationEventResponse(
		ApplicationStatus fromStatus,
		ApplicationStatus toStatus,
		String note,
		Instant at) {
}
