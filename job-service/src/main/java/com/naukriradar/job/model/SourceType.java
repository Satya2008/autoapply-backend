package com.naukriradar.job.model;

/** How a source is read. Each type has one {@code JobSourceProvider}. */
public enum SourceType {
	/** HTTP API returning JSON, mapped with JsonPath. */
	REST_JSON,
	/**
	 * An HTML page, mapped with CSS selectors. Only pages the site's robots.txt allows are read,
	 * so this is for career pages that welcome it, not for boards that forbid scraping.
	 */
	HTML_SCRAPE
}
