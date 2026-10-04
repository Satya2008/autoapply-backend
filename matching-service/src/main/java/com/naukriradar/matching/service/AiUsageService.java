package com.naukriradar.matching.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.naukriradar.common.exception.BadRequestException;
import com.naukriradar.matching.config.AiProperties;
import com.naukriradar.matching.dto.response.AiUsageRow;
import com.naukriradar.matching.model.AiUsage;
import com.naukriradar.matching.repository.AiUsageRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records what each AI call cost, and reports on it. */
@Service
public class AiUsageService {

	/** Report groupings and the SQL each one groups by; nothing from the request reaches SQL. */
	private static final Map<String, String> GROUPS = Map.of(
			"provider", "provider",
			"model", "model",
			"purpose", "purpose",
			"user", "COALESCE(user_id, '-')",
			"day", "DATE_FORMAT(at, '%Y-%m-%d')");

	private final AiUsageRepository repository;
	private final JdbcTemplate jdbc;
	private final AiProperties properties;
	private final Clock clock = Clock.systemUTC();

	public AiUsageService(AiUsageRepository repository, JdbcTemplate jdbc, AiProperties properties) {
		this.repository = repository;
		this.jdbc = jdbc;
		this.properties = properties;
	}

	/** In micro-dollars: a price per million tokens is exactly micro-dollars per token. */
	public long cost(String model, long tokensIn, long tokensOut) {
		AiProperties.Price price = properties.prices().get(model);
		if (price == null) {
			return 0;
		}
		return price.input().multiply(BigDecimal.valueOf(tokensIn))
				.add(price.output().multiply(BigDecimal.valueOf(tokensOut)))
				.setScale(0, RoundingMode.HALF_UP)
				.longValueExact();
	}

	@Transactional
	public void record(String userId, String purpose, String provider, String model, long tokensIn, long tokensOut,
			long costMicros, long latencyMs, boolean success) {
		repository.save(new AiUsage(userId, purpose, provider, model, tokensIn, tokensOut, costMicros, latencyMs, success,
				clock.instant()));
	}

	/** Spent by this user since midnight UTC. */
	@Transactional(readOnly = true)
	public long spentToday(String userId) {
		Instant midnight = LocalDate.now(clock).atStartOfDay(ZoneOffset.UTC).toInstant();
		return repository.costSince(userId, midnight);
	}

	@Transactional(readOnly = true)
	public List<AiUsageRow> report(Instant from, Instant to, String groupBy) {
		String group = GROUPS.get(groupBy == null ? "provider" : groupBy.strip().toLowerCase());
		if (group == null) {
			throw new BadRequestException("groupBy must be one of " + GROUPS.keySet() + ".");
		}
		Instant end = to == null ? clock.instant() : to;
		Instant start = from == null ? end.minusSeconds(30L * 24 * 3600) : from;
		if (!start.isBefore(end)) {
			throw new BadRequestException("from must be before to.");
		}
		String sql = "SELECT " + group + " AS grp, COUNT(*) AS calls, SUM(success = 0) AS failed,"
				+ " SUM(tokens_in) AS tokens_in, SUM(tokens_out) AS tokens_out, SUM(cost_micros) AS cost,"
				+ " AVG(latency_ms) AS latency FROM ai_usage WHERE at >= ? AND at < ? GROUP BY grp ORDER BY cost DESC, grp";
		// times are stored in UTC (hibernate.jdbc.time_zone), so bind them in UTC too
		Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		return jdbc.query(sql, ps -> {
			ps.setTimestamp(1, Timestamp.from(start), utc);
			ps.setTimestamp(2, Timestamp.from(end), utc);
		}, (rs, n) -> new AiUsageRow(rs.getString("grp"), rs.getLong("calls"), rs.getLong("failed"),
				rs.getLong("tokens_in"), rs.getLong("tokens_out"),
				BigDecimal.valueOf(rs.getLong("cost")).movePointLeft(6), Math.round(rs.getDouble("latency"))));
	}

}
