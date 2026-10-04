package com.naukriradar.common.events;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

/**
 * What goes over Kafka, around every payload.
 *
 * @param eventId unique per event; consumers use it to ignore a second delivery
 * @param type what happened, e.g. {@code MatchRunCompleted}
 * @param version of the payload's shape: a consumer that only knows version 1 can tell a
 *     version 2 event apart instead of misreading it
 * @param key the Kafka key (user id, run id): same key, same partition, same order
 */
public record EventEnvelope(String eventId, String type, int version, Instant occurredAt, String source, String key,
		JsonNode payload) {
}
