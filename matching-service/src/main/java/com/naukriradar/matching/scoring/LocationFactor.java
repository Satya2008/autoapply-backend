package com.naukriradar.matching.scoring;

import java.util.Map;
import java.util.regex.Pattern;

import com.naukriradar.matching.client.CandidateJob;
import org.springframework.stereotype.Component;

@Component
public class LocationFactor implements ScoringFactor {

	@Override
	public String name() {
		return "location";
	}

	@Override
	public FactorResult score(MatchContext context, CandidateJob job) {
		boolean remoteOk = context.profile().remoteOk();
		if (job.remote() && remoteOk) {
			return new FactorResult(1, "Remote, and you're open to remote work.");
		}
		String location = job.location() == null ? "" : job.location();
		for (Map.Entry<String, Pattern> preferred : context.locationPatterns().entrySet()) {
			if (preferred.getValue().matcher(location).find()) {
				return new FactorResult(1, "In " + preferred.getKey() + ", one of your locations.");
			}
		}
		if (job.remote()) {
			return new FactorResult(0.3, "Remote, but you haven't said you're open to remote work.");
		}
		if (context.locationPatterns().isEmpty()) {
			return FactorResult.unknown("No preferred locations on your profile.");
		}
		if (location.isBlank()) {
			return FactorResult.unknown("The posting gives no location.");
		}
		return new FactorResult(0, location + " isn't one of your locations.");
	}

}
