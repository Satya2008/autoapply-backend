package com.naukriradar.matching.scoring;

import java.time.Duration;

import com.naukriradar.matching.client.CandidateJob;
import org.springframework.stereotype.Component;

/** Newer postings first: early applicants get read. */
@Component
public class RecencyFactor implements ScoringFactor {

	@Override
	public String name() {
		return "recency";
	}

	@Override
	public FactorResult score(MatchContext context, CandidateJob job) {
		if (job.postedAt() == null) {
			return FactorResult.unknown("Posting date unknown.");
		}
		long days = Math.max(0, Duration.between(job.postedAt(), context.now()).toDays());
		double score = days <= 1 ? 1 : days <= 3 ? 0.9 : days <= 7 ? 0.75 : days <= 14 ? 0.55 : days <= 30 ? 0.35 : 0.15;
		return new FactorResult(score, days == 0 ? "Posted today." : "Posted " + days + (days == 1 ? " day" : " days") + " ago.");
	}

}
