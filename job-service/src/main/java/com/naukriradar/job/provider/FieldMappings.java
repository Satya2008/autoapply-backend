package com.naukriradar.job.provider;

/**
 * A field mapping is normally a path into each item (JsonPath or CSS selector). One starting
 * with {@code =} is a fixed value instead, for what a board leaves out of its items: Lever and
 * Ashby never say the company's name, so a source for one company maps {@code company} to
 * {@code =Acme}.
 */
public final class FieldMappings {

	public static final String LITERAL_PREFIX = "=";

	private FieldMappings() {
	}

	public static boolean isLiteral(String mapping) {
		return mapping != null && mapping.startsWith(LITERAL_PREFIX);
	}

	public static String literal(String mapping) {
		return mapping.substring(LITERAL_PREFIX.length());
	}

}
