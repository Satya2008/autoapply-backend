package com.naukriradar.core.dto.response;

import java.util.Map;

import com.naukriradar.core.model.ApplicationStatus;

/**
 * @param byStatus every status, with 0 for the ones not in use, so dashboards don't have to guess
 * @param sent applications that reached an employer (submitted or applied, and anything after)
 * @param automatedToday counted against today's automatic limit
 */
public record ApplicationStatsResponse(
		long total,
		Map<ApplicationStatus, Long> byStatus,
		long sent,
		long automatedToday,
		int dailyLimit) {
}
