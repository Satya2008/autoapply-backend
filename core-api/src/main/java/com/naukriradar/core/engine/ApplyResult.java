package com.naukriradar.core.engine;

import com.naukriradar.core.model.ApplicationStatus;
import com.naukriradar.core.model.SubmittedVia;

/**
 * @param status SIMULATED or SUBMITTED
 * @param note what happened, for the timeline
 */
public record ApplyResult(ApplicationStatus status, SubmittedVia via, String note) {
}
