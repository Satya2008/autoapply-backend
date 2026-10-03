package com.naukriradar.core.util;

/** Indian digit grouping: 15,00,000. The JDK's NumberFormat only groups in threes. */
public final class IndianNumberFormat {

	private IndianNumberFormat() {
	}

	public static String format(long amount) {
		String digits = Long.toString(Math.abs(amount));
		String sign = amount < 0 ? "-" : "";
		if (digits.length() <= 3) {
			return sign + digits;
		}
		StringBuilder rest = new StringBuilder(digits.substring(0, digits.length() - 3));
		for (int i = rest.length() - 2; i > 0; i -= 2) {
			rest.insert(i, ',');
		}
		return sign + rest + "," + digits.substring(digits.length() - 3);
	}

}
