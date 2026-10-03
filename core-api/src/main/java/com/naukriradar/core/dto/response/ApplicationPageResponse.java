package com.naukriradar.core.dto.response;

import java.util.List;

/**
 * @param nextCursor pass as {@code cursor} for the next page; null on the last page
 */
public record ApplicationPageResponse(List<ApplicationSummaryResponse> items, String nextCursor) {
}
