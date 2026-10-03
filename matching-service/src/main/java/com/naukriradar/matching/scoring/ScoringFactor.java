package com.naukriradar.matching.scoring;

import com.naukriradar.matching.client.CandidateJob;

/**
 * One reason a job fits a candidate. A new reason is a new class; its weight comes from
 * configuration under {@code naukriradar.matching.weights.<name>}.
 */
public interface ScoringFactor {

	String name();

	FactorResult score(MatchContext context, CandidateJob job);

}
