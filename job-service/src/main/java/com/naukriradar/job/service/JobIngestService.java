package com.naukriradar.job.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.job.config.CacheConfig;
import com.naukriradar.job.config.JobsProperties;
import com.naukriradar.job.dto.response.DryRunResponse;
import com.naukriradar.job.dto.response.FetchResultResponse;
import com.naukriradar.job.dto.response.JobPreview;
import com.naukriradar.job.exception.JobSourceFetchException;
import com.naukriradar.job.mapper.JobMapper;
import com.naukriradar.job.model.JobField;
import com.naukriradar.job.model.JobSource;
import com.naukriradar.job.model.RunStatus;
import com.naukriradar.job.model.SourceType;
import com.naukriradar.job.normalizer.JobNormalizer;
import com.naukriradar.job.normalizer.NormalizationResult;
import com.naukriradar.job.normalizer.NormalizedJob;
import com.naukriradar.job.provider.FetchRequest;
import com.naukriradar.job.provider.JobSourceProvider;
import com.naukriradar.job.provider.RawJob;
import com.naukriradar.job.repository.JobBatchWriter;
import com.naukriradar.job.repository.JobBatchWriter.FingerprintedJob;
import com.naukriradar.job.repository.JobBatchWriter.WriteCounts;
import com.naukriradar.job.repository.JobSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs one source: fetch every page, clean each item, then save. The HTTP calls happen
 * outside any transaction so a slow board never holds a database connection. A failing
 * board is recorded on the source and returned as FAILED, never thrown at the caller.
 */
@Service
public class JobIngestService {

	private static final Logger log = LoggerFactory.getLogger(JobIngestService.class);

	private static final int SAMPLE_SIZE = 10;
	private static final int MAX_PROBLEMS = 20;
	private static final int WRITE_ATTEMPTS = 3;

	private final JobSourceRepository sourceRepository;
	private final JobBatchWriter writer;
	private final FingerprintService fingerprints;
	private final Map<SourceType, JobSourceProvider> providers = new EnumMap<>(SourceType.class);
	private final JobNormalizer normalizer;
	private final JobMapper jobMapper;
	private final SourceRunLocks runLocks;
	private final JobsProperties properties;
	private final TransactionTemplate transaction;
	private final Clock clock;

	public JobIngestService(JobSourceRepository sourceRepository, JobBatchWriter writer, FingerprintService fingerprints,
			List<JobSourceProvider> providers, JobNormalizer normalizer, JobMapper jobMapper, SourceRunLocks runLocks,
			JobsProperties properties, PlatformTransactionManager transactionManager) {
		this.sourceRepository = sourceRepository;
		this.writer = writer;
		this.fingerprints = fingerprints;
		providers.forEach(provider -> this.providers.put(provider.supports(), provider));
		this.normalizer = normalizer;
		this.jobMapper = jobMapper;
		this.runLocks = runLocks;
		this.properties = properties;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = Clock.systemUTC();
	}

	/**
	 * Fetches and saves. Works on disabled sources too, so an admin can try a fix by hand.
	 * Cached job details are dropped afterwards: the fetch may have changed any of them.
	 */
	@CacheEvict(cacheNames = CacheConfig.JOB_DETAIL, allEntries = true)
	public FetchResultResponse fetch(String sourceId) {
		JobSource source = loadForRun(sourceId);
		if (!runLocks.tryAcquire(source.getId())) {
			throw new ConflictException("A fetch for source " + source.getCode() + " is already running.");
		}
		Instant started = clock.instant();
		try {
			Collected collected = collect(source, started);
			List<FingerprintedJob> jobs = collected.jobs().stream()
					.map(job -> new FingerprintedJob(job, fingerprints.fingerprint(job)))
					.toList();
			WriteCounts counts = writeWithRetry(source.getCode(), jobs, started);
			String message = "Fetched " + collected.received() + " jobs: " + counts.inserted() + " new, "
					+ counts.updated() + " updated, " + counts.duplicates() + " already listed by another board, "
					+ collected.skipped() + " skipped.";
			transaction.executeWithoutResult(status -> sourceRepository.findById(source.getId())
					.ifPresent(s -> s.recordSuccess(clock.instant(), message)));
			return new FetchResultResponse(source.getCode(), RunStatus.SUCCESS, collected.pages(), collected.received(),
					counts.inserted(), counts.updated(), counts.duplicates(), collected.skipped(), message, false,
					elapsed(started));
		}
		catch (RuntimeException ex) {
			String message = failureMessage(source, ex);
			Boolean disabled = transaction.execute(status -> sourceRepository.findById(source.getId())
					.map(s -> s.recordFailure(clock.instant(), message, properties.disableAfterFailures()))
					.orElse(false));
			if (Boolean.TRUE.equals(disabled)) {
				log.warn("Job source {} disabled after {} failed runs in a row", source.getCode(),
						properties.disableAfterFailures());
			}
			return new FetchResultResponse(source.getCode(), RunStatus.FAILED, 0, 0, 0, 0, 0, 0, message,
					Boolean.TRUE.equals(disabled), elapsed(started));
		}
		finally {
			runLocks.release(source.getId());
		}
	}

	/** Runs the source but saves nothing and leaves its run status alone. */
	public DryRunResponse dryRun(String sourceId) {
		JobSource source = loadForRun(sourceId);
		try {
			Collected collected = collect(source, clock.instant());
			List<JobPreview> sample = collected.jobs().stream()
					.limit(SAMPLE_SIZE)
					.map(jobMapper::toPreview)
					.toList();
			return new DryRunResponse(source.getCode(), true, null, collected.pages(), collected.received(),
					collected.jobs().size(), collected.skipped(), sample, collected.problems());
		}
		catch (JobSourceFetchException ex) {
			return new DryRunResponse(source.getCode(), false, ex.getMessage(), 0, 0, 0, 0, List.of(), List.of());
		}
	}

	private JobSource loadForRun(String sourceId) {
		return transaction.execute(status -> sourceRepository.findWithConfigById(sourceId)
				.orElseThrow(() -> new NotFoundException("No job source " + sourceId + ".")));
	}

	private Collected collect(JobSource source, Instant now) {
		JobSourceProvider provider = providers.get(source.getType());
		if (provider == null) {
			throw new JobSourceFetchException("No provider for source type " + source.getType() + ".");
		}
		List<NormalizedJob> jobs = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		int received = 0;
		int skipped = 0;
		int pages = 0;
		Object previousFirstId = null;

		for (int page = 1; page <= source.getMaxPages(); page++) {
			if (Thread.currentThread().isInterrupted()) {
				throw new JobSourceFetchException("Stopped: the run's time limit was reached.");
			}
			List<RawJob> items = provider.fetchPage(source, new FetchRequest("", page));
			pages++;
			if (items.isEmpty()) {
				break;
			}
			// a board that ignores the page parameter returns page 1 forever
			Object firstId = items.get(0).get(JobField.EXTERNAL_ID);
			if (page > 1 && firstId != null && Objects.equals(firstId, previousFirstId)) {
				break;
			}
			previousFirstId = firstId;

			received += items.size();
			for (RawJob item : items) {
				NormalizationResult result = normalizer.normalize(item, now);
				if (!result.isOk()) {
					skipped++;
					addProblem(problems, result.skipReason());
				}
				// MySQL compares ids case-insensitively, so duplicates are judged the same way
				else if (!seen.add(result.job().externalId().toLowerCase(Locale.ROOT))) {
					skipped++;
					addProblem(problems, result.job().externalId() + ": repeated in this run");
				}
				else {
					jobs.add(result.job());
				}
			}
		}
		return new Collected(pages, received, skipped, jobs, problems);
	}

	/**
	 * Sources run in parallel and can save the same posting at the same moment. Ordered writes
	 * make a deadlock unlikely; if InnoDB still picks this transaction as the victim, the whole
	 * write is safe to repeat, as MySQL itself suggests.
	 */
	private WriteCounts writeWithRetry(String sourceCode, List<FingerprintedJob> jobs, Instant now) {
		for (int attempt = 1;; attempt++) {
			try {
				return writer.write(sourceCode, jobs, now);
			}
			catch (PessimisticLockingFailureException ex) {
				if (attempt >= WRITE_ATTEMPTS) {
					throw ex;
				}
				log.info("Lock conflict saving jobs for {}, retrying (attempt {})", sourceCode, attempt + 1);
				pause(attempt);
			}
		}
	}

	private static void pause(int attempt) {
		try {
			Thread.sleep(50L * attempt + ThreadLocalRandom.current().nextInt(50));
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new JobSourceFetchException("Stopped while retrying the save.", ex);
		}
	}

	private static String failureMessage(JobSource source, RuntimeException ex) {
		if (ex instanceof JobSourceFetchException) {
			return ex.getMessage();
		}
		log.error("Run of job source {} failed unexpectedly", source.getCode(), ex);
		return "Unexpected error while saving jobs (" + ex.getClass().getSimpleName() + "). See service logs.";
	}

	private static void addProblem(List<String> problems, String problem) {
		if (problems.size() < MAX_PROBLEMS) {
			problems.add(problem);
		}
	}

	private long elapsed(Instant started) {
		return Duration.between(started, clock.instant()).toMillis();
	}

	private record Collected(int pages, int received, int skipped, List<NormalizedJob> jobs, List<String> problems) {
	}

}
