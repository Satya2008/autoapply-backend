package com.naukriradar.worker.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** How to fill one portal's form: field name to CSS selector, plus "submit" and "success". */
public record PortalConfig(String domain, String name, String riskBand, Map<String, String> selectors) {

	/** Keeps the admin's order: fields are filled top to bottom as configured. */
	public PortalConfig {
		selectors = selectors == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(selectors));
	}

}
