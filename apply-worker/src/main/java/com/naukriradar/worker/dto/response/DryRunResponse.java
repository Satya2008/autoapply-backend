package com.naukriradar.worker.dto.response;

import java.util.List;

/** @param screenshot key of a screenshot of the filled form */
public record DryRunResponse(List<String> filled, List<String> missing, String note, String screenshot) {
}
