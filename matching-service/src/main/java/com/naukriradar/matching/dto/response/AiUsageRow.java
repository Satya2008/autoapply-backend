package com.naukriradar.matching.dto.response;

import java.math.BigDecimal;

/** AI spend for one group (a provider, a model, a day...). */
public record AiUsageRow(String group, long calls, long failed, long tokensIn, long tokensOut, BigDecimal costUsd,
		long averageLatencyMs) {
}
