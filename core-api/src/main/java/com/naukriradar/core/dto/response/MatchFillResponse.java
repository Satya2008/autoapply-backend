package com.naukriradar.core.dto.response;

/** "Fill for me" on a match: the application it made (or already had), and what the fill did. */
public record MatchFillResponse(String applicationId, FillReviewResponse fill) {
}
