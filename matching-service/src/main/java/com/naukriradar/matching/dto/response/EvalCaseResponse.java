package com.naukriradar.matching.dto.response;

import java.time.Instant;

import com.naukriradar.matching.dto.request.EvalCaseRequest;

public record EvalCaseResponse(String id, String name, EvalCaseRequest.Profile profile, EvalCaseRequest.Job job,
		int expectedScore, String notes, Instant createdAt) {
}
