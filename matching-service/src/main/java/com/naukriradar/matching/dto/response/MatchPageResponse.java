package com.naukriradar.matching.dto.response;

import java.util.List;

/**
 * @param nextCursor pass as {@code cursor} for the next page; null on the last page
 */
public record MatchPageResponse(List<MatchSummaryResponse> items, String nextCursor) {
}
