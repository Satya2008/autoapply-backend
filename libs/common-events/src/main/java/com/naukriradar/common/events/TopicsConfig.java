package com.naukriradar.common.events;

import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Every topic and its dead letter twin. Three partitions: events with the same key (a user
 * id) always land in the same partition and stay in order, while different users are
 * handled in parallel.
 */
final class TopicsConfig {

	static final int PARTITIONS = 3;

	private TopicsConfig() {
	}

	static KafkaAdmin.NewTopics all(EventTopics names, int replicas) {
		List<NewTopic> topics = new ArrayList<>();
		for (String topic : List.of(Topics.JOBS_INGESTED, Topics.JOBS_PARSED, Topics.MATCH_CREATED, Topics.APPLY_REQUESTED,
				Topics.APPLY_COMPLETED, Topics.NOTIFY_REQUESTED)) {
			topics.add(TopicBuilder.name(names.name(topic)).partitions(PARTITIONS).replicas(replicas).build());
			topics.add(TopicBuilder.name(Topics.dlq(names.name(topic))).partitions(1).replicas(replicas).build());
		}
		return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
	}

}
