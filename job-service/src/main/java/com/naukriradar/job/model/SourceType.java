package com.naukriradar.job.model;

/** How a source is read. Each type has one {@code JobSourceProvider}. */
public enum SourceType {
	/** HTTP API returning JSON, mapped with JsonPath. */
	REST_JSON
}
