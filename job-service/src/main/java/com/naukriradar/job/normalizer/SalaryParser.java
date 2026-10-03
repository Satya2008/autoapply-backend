package com.naukriradar.job.normalizer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads salary amounts written as numbers or text: {@code 85000}, {@code "50,000"},
 * {@code "60k - 80k"}, {@code "12 LPA"}, {@code "8-12 lakh"}. Returns whole currency units.
 */
final class SalaryParser {

	/** Above this an amount is almost certainly a parsing accident (e.g. a phone number). */
	private static final long MAX_REASONABLE = 10_000_000_000L;

	private static final Pattern AMOUNT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(k|lpa|lakhs?|lacs?|l|cr|crore|m|mn)?\\b");

	private SalaryParser() {
	}

	/** Every amount in the value, in order. A range gives two. */
	static List<Long> amounts(Object value) {
		List<Long> result = new ArrayList<>();
		if (value == null) {
			return result;
		}
		if (value instanceof Number number) {
			add(result, BigDecimal.valueOf(number.doubleValue()));
			return result;
		}
		String text = value.toString().toLowerCase(Locale.ROOT).replace(",", "").replace("_", "");
		Matcher matcher = AMOUNT.matcher(text);
		List<String> units = new ArrayList<>();
		List<BigDecimal> numbers = new ArrayList<>();
		while (matcher.find()) {
			numbers.add(new BigDecimal(matcher.group(1)));
			units.add(matcher.group(2));
		}
		// "8-12 lakh": the unit written once applies to both ends of the range
		String sharedUnit = units.isEmpty() ? null : units.get(units.size() - 1);
		for (int i = 0; i < numbers.size(); i++) {
			String unit = units.get(i) != null ? units.get(i) : sharedUnit;
			add(result, numbers.get(i).multiply(multiplier(unit)));
		}
		return result;
	}

	private static BigDecimal multiplier(String unit) {
		if (unit == null) {
			return BigDecimal.ONE;
		}
		return switch (unit) {
			case "k" -> BigDecimal.valueOf(1_000);
			case "m", "mn" -> BigDecimal.valueOf(1_000_000);
			case "cr", "crore" -> BigDecimal.valueOf(10_000_000);
			default -> BigDecimal.valueOf(100_000); // lakh / lpa / l
		};
	}

	private static void add(List<Long> result, BigDecimal amount) {
		if (amount.signum() > 0 && amount.compareTo(BigDecimal.valueOf(MAX_REASONABLE)) <= 0) {
			result.add(amount.longValue());
		}
	}

}
