package com.naukriradar.common.events;

import com.naukriradar.common.redis.lock.DistributedLock;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.json.JsonMapper;

/**
 * Outbox, relay, idempotent consumers and dead letters for every service that has this
 * library. Runs before Spring's Kafka setup so the listener containers pick up the error
 * handler defined here.
 */
@AutoConfiguration(
		afterName = { "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
				"com.naukriradar.common.redis.config.RedisCommonAutoConfiguration" },
		beforeName = "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration")
@EnableConfigurationProperties(EventsProperties.class)
public class EventsAutoConfiguration {

	private static final Logger log = LoggerFactory.getLogger(EventsAutoConfiguration.class);

	@Bean
	EventTables eventTables(JdbcTemplate jdbc) {
		EventTables tables = new EventTables(jdbc);
		tables.create();
		return tables;
	}

	@Bean
	EventTopics eventTopics(@Value("${naukriradar.events.prefix:}") String prefix,
			@Value("${naukriradar.events.group-suffix:}") String groupSuffix) {
		return new EventTopics(prefix, groupSuffix);
	}

	@Bean
	OutboxWriter outboxWriter(EventTables tables, JdbcTemplate jdbc, JsonMapper json, EventTopics eventTopics,
			@Value("${spring.application.name:app}") String application) {
		return new OutboxWriter(jdbc, json, application, eventTopics);
	}

	@Bean
	OutboxRelay outboxRelay(EventTables tables, JdbcTemplate jdbc, KafkaTemplate<String, String> kafka,
			DistributedLock lock, EventsProperties properties) {
		return new OutboxRelay(jdbc, kafka, lock, properties.relayEvery());
	}

	@Bean
	IdempotentConsumer idempotentConsumer(EventTables tables, JdbcTemplate jdbc, JsonMapper json,
			PlatformTransactionManager transactionManager) {
		return new IdempotentConsumer(jdbc, json, transactionManager);
	}

	@Bean
	DeadLetters deadLetters(EventTables tables, JdbcTemplate jdbc, OutboxWriter outbox, JsonMapper json) {
		return new DeadLetters(jdbc, outbox, json);
	}

	@Bean
	OutboxStats outboxStats(EventTables tables, JdbcTemplate jdbc) {
		return new OutboxStats(jdbc);
	}

	/**
	 * A failed event is retried a few times; then it is saved as a dead letter and sent to
	 * the topic's {@code .dlq}, and the consumer moves on to the next one.
	 */
	@Bean
	CommonErrorHandler eventErrorHandler(KafkaTemplate<String, String> kafka, DeadLetters deadLetters,
			EventsProperties properties) {
		DeadLetterPublishingRecoverer toDlq = new DeadLetterPublishingRecoverer(kafka,
				(record, ex) -> new TopicPartition(Topics.dlq(record.topic()), -1));
		DefaultErrorHandler handler = new DefaultErrorHandler((record, ex) -> {
			log.warn("Event on {} failed {} times; moving it to the dead letters: {}", record.topic(),
					properties.retries() + 1, ex.getMessage());
			deadLetters.record(record, ex);
			toDlq.accept(record, ex);
		}, new FixedBackOff(properties.retryBackoff().toMillis(), properties.retries()));
		return handler;
	}

	@Bean
	KafkaAdmin.NewTopics eventTopicDefinitions(EventTopics eventTopics, @Value("${naukriradar.events.replicas:1}") int replicas) {
		return TopicsConfig.all(eventTopics, replicas);
	}

	@Bean
	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
	EventsAdminController eventsAdminController(DeadLetters deadLetters, OutboxStats outboxStats) {
		return new EventsAdminController(deadLetters, outboxStats);
	}

}
