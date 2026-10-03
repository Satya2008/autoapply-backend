package com.naukriradar.core.dto.response;

import java.time.Instant;

import com.naukriradar.core.model.ApplicationStatus;
import com.naukriradar.core.model.RiskBand;
import com.naukriradar.core.model.SubmittedVia;

public record ApplicationSummaryResponse(
		String id,
		String jobId,
		String title,
		String company,
		String location,
		String applyUrl,
		int matchScore,
		ApplicationStatus status,
		RiskBand riskBand,
		SubmittedVia submittedVia,
		Instant createdAt,
		Instant updatedAt) {
}
