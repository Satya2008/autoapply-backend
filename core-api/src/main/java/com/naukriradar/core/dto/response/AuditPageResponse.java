package com.naukriradar.core.dto.response;

import java.util.List;

/**
 * @param nextCursor pass as {@code cursor} for older entries; null on the last page
 */
public record AuditPageResponse(List<AuditEntryResponse> items, String nextCursor) {
}
