package com.naukriradar.job.event;

import java.util.UUID;

import com.naukriradar.common.events.IdempotentConsumer;
import com.naukriradar.common.events.OutboxWriter;
import com.naukriradar.common.events.Topics;
import com.naukriradar.job.service.JobParsingService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * jobs.ingested -> parse the new jobs with AI -> jobs.parsed. Matching listens for
 * jobs.parsed, so it scores jobs with their requirements already known. With AI off the
 * round parses nothing and jobs.parsed still goes out: matching doesn't wait for AI.
 */
@Component
public class JobParsingConsumer {

	static final String CONSUMER = "job-service.parsing";

	private final IdempotentConsumer idempotent;
	private final JobParsingService parsing;
	private final OutboxWriter outbox;
	private final TransactionTemplate transaction;

	public JobParsingConsumer(IdempotentConsumer idempotent, JobParsingService parsing, OutboxWriter outbox,
			PlatformTransactionManager transactionManager) {
		this.idempotent = idempotent;
		this.parsing = parsing;
		this.outbox = outbox;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	@KafkaListener(topics = "#{@eventTopics.name('" + Topics.JOBS_INGESTED + "')}", groupId = "#{@eventTopics.group('" + CONSUMER + "')}")
	public void onJobsIngested(String message) {
		// parsing calls AI for minutes; it only touches unparsed jobs, so running it twice is harmless
		idempotent.handleRepeatable(CONSUMER, message, event -> {
			int parsed = parsing.parsePending();
			String round = UUID.randomUUID().toString();
			transaction.executeWithoutResult(status -> outbox.publish(Topics.JOBS_PARSED, round, "JobsParsed",
					new JobsParsed(round, event.eventId(), parsed)));
		});
	}

	/** Payload of {@link Topics#JOBS_PARSED}. */
	public record JobsParsed(String roundId, String afterEventId, int parsedJobs) {
	}

}
