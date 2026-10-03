package com.naukriradar.matching.scoring;

import com.naukriradar.matching.client.CandidateJob;
import org.springframework.stereotype.Component;

/**
 * Whether the posting's top salary reaches what the candidate expects. Expectations are in
 * INR, so other currencies, or postings without one, can't be compared and score neutral.
 */
@Component
public class SalaryFactor implements ScoringFactor {

	static final String HOME_CURRENCY = "INR";

	@Override
	public String name() {
		return "salary";
	}

	@Override
	public FactorResult score(MatchContext context, CandidateJob job) {
		Long expected = context.profile().expectedSalary();
		if (expected == null || expected <= 0) {
			return FactorResult.unknown("No expected salary on your profile.");
		}
		Long top = job.salaryMax() != null ? job.salaryMax() : job.salaryMin();
		if (top == null || top <= 0) {
			return FactorResult.unknown("Salary not listed.");
		}
		if (job.currency() == null) {
			return FactorResult.unknown("Salary has no currency, so it can't be compared.");
		}
		if (!HOME_CURRENCY.equals(job.currency())) {
			return FactorResult.unknown("Salary is in " + job.currency() + ", so it can't be compared.");
		}
		if (top >= expected) {
			return new FactorResult(1, "Pays up to ₹" + format(top) + ", at least your ₹" + format(expected) + ".");
		}
		double shortfall = (double) (expected - top) / expected;
		// 25% short scores 0.5; 50% or more short scores 0
		return new FactorResult(1 - shortfall * 2,
				"Pays up to ₹" + format(top) + ", " + Math.round(shortfall * 100) + "% below your ₹" + format(expected) + ".");
	}

	/** Indian grouping, 15,00,000. The JDK's NumberFormat only does groups of three. */
	static String format(long amount) {
		String digits = Long.toString(Math.abs(amount));
		if (digits.length() <= 3) {
			return (amount < 0 ? "-" : "") + digits;
		}
		StringBuilder rest = new StringBuilder(digits.substring(0, digits.length() - 3));
		for (int i = rest.length() - 2; i > 0; i -= 2) {
			rest.insert(i, ',');
		}
		return (amount < 0 ? "-" : "") + rest + "," + digits.substring(digits.length() - 3);
	}

}
