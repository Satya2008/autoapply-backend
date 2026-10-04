package com.naukriradar.common.events;

/**
 * Turns the logical topic and consumer-group names in {@link Topics} into the real ones,
 * with an optional prefix: tests use "test." so they never mix with a development setup on
 * the same broker, and a group suffix so a test can read with a fresh consumer group.
 * Listeners refer to it from their annotations, e.g.
 * {@code topics = "#{@eventTopics.name('jobs.ingested')}"}.
 */
public class EventTopics {

	private final String prefix;
	private final String groupSuffix;

	public EventTopics(String prefix, String groupSuffix) {
		this.prefix = prefix == null ? "" : prefix;
		this.groupSuffix = groupSuffix == null ? "" : groupSuffix;
	}

	public String name(String topic) {
		return topic.startsWith(prefix) ? topic : prefix + topic;
	}

	public String group(String group) {
		return prefix + group + groupSuffix;
	}

}
