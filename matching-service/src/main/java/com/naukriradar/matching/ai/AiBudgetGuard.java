package com.naukriradar.matching.ai;

import java.math.BigDecimal;

import com.naukriradar.matching.config.AiProperties;
import com.naukriradar.matching.service.AiUsageService;
import org.springframework.stereotype.Component;

/** One user can't spend more than the daily budget on AI; past it, AI is skipped for them until tomorrow (UTC). */
@Component
public class AiBudgetGuard {

	private final AiUsageService usage;
	private final AiProperties properties;

	public AiBudgetGuard(AiUsageService usage, AiProperties properties) {
		this.usage = usage;
		this.properties = properties;
	}

	/** @throws AiBudgetExceededException if the user's budget for today is used up */
	public void check(String userId) {
		if (userId == null) {
			return;
		}
		long budget = properties.dailyBudgetUsd().movePointRight(6).longValue();
		long spent = usage.spentToday(userId);
		if (spent >= budget) {
			throw new AiBudgetExceededException("Today's AI budget of $" + properties.dailyBudgetUsd() + " is used up ($"
					+ BigDecimal.valueOf(spent).movePointLeft(6) + " spent).");
		}
	}

}
