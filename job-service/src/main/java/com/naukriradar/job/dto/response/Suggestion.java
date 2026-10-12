package com.naukriradar.job.dto.response;

/**
 * One thing to offer while someone types.
 *
 * @param kind place, skill or title
 * @param count active jobs it would find, when known
 */
public record Suggestion(String label, String kind, Long count) {
}
