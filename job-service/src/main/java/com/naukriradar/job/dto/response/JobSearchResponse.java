package com.naukriradar.job.dto.response;

import java.util.List;

/**
 * @param nextCursor pass as {@code cursor} to get the next page; null on the last page
 */
public record JobSearchResponse(List<JobSummaryResponse> items, String nextCursor) {
}
