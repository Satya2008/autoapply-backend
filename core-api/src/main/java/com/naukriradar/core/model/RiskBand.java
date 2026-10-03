package com.naukriradar.core.model;

/** How risky it is to automate applying on a site. */
public enum RiskBand {
	/** Applicant tracking systems built for direct applications (Greenhouse, Lever...). */
	LOW,
	/** Unknown sites: prepared for the candidate, not automated. */
	MEDIUM,
	/** Sites that ban bots, where automation could get the candidate's account blocked. Never automated. */
	HIGH
}
