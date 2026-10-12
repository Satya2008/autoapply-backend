package com.naukriradar.core.engine;

import com.naukriradar.core.model.ApplicationStatus;
import com.naukriradar.core.model.SubmittedVia;

/**
 * @param status SUBMITTED, or NEEDS_YOU when the engine hands it to the candidate
 * @param note what happened, for the timeline
 */
public record ApplyResult(ApplicationStatus status, SubmittedVia via, String note) {
}
