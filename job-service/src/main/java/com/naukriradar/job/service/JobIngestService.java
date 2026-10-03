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
import java.util.function.Function;
import java.util.stream.Collectors;

import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.job.config.JobsProperties;
import com.naukriradar.job.dto.response.DryRunResponse;
import com.naukriradar.job.dto.response.FetchResultResponse;
import com.naukriradar.job.dto.response.JobPreview;
import com.naukriradar.job.exception.JobSourceFetchException;
import com.naukriradar.job.mapper.JobMapper;
import com.naukriradar.job.model.Job;
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
import com.naukriradar.job.repository.JobRepository;
import com.naukriradar.job.repository.JobSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs a source: fetch every page, clean each item, then save new jobs and refresh known
 * ones. The HTTP calls happen outside any transaction so a slow board never holds a
 * database connection. A failing board is recorded on the source, never thrown at the caller.
 */
@Service
public class JobIngestService {

	private static final Logger log = LoggerFactory.getLogger(JobIngestService.class);

	private static final int LOOKUP_BATCH = 500;
	private static final int SAMPLE_SIZE = 10;
	private static final int MAX_PROBLEMS = 20;

	private final JobSourceRepository sourceRepository;
	private final JobRepository jobRepository;
	private final Map<SourceType, JobSourceProvider> providers = new EnumMap<>(SourceType.class);
	private final JobNormalizer normalizer;
	private final JobMapper jobMapper;
	private final SourceRunLocks runLocks;
	private final JobsProperties properties;
	private final TransactionTemplate transaction;
	private final Clock clock;

	public JobIngestService(JobSourceRepository sourceRepository, JobRepository jobRepository,
			List<JobSourceProvider> providers, JobNormalizer normalizer, JobMapper jobMapper, SourceRunLocks runLocks,
			JobsProperties properties, PlatformTransactionManager transactionManager) {
		this.sourceRepository = sourceRepository;
		this.jobRepository = jobRepository;
		providers.forEach(provider -> this.providers.put(provider.supports(), provider));
		this.normalizer = normalizer;
		this.jobMapper = jobMapper;
		this.runLocks = runLocks;
		this.properties = properties;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = Clock.systemUTC();
	}

	/** Fetches and saves. Works on disabled sources too, so an admin can try a fix by hand. */
	public FetchResultResponse fetch(String sourceId) {
		JobSource source = loadForRun(sourceId);
		if (!runLocks.tryAcquire(source.getId())) {
			throw new ConflictException("A fetch for this source is already running.");
		}
		Instant started = clock.instant();
		try {
			Collected collected = collect(source, started);
			SaveCounts counts = transaction.execute(status -> save(source.getCode(), collected.jobs(), started));
			String message = "Fetched " + collected.received() + " jobs: " + counts.inserted() + " new, "
					+ counts.updated() + " updated, " + collected.skipped() + " skipped.";
			transaction.executeWithoutResult(status -> sourceRepository.findById(source.getId())
					.ifPresent(s -> s.recordSuccess(clock.instant(), message)));
			return new FetchResultResponse(source.getCode(), RunStatus.SUCCESS, collected.pages(), collected.received(),
					counts.inserted(), counts.updated(), collected.skipped(), message, false, elapsed(started));
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
			return new FetchResultResponse(source.getCode(), RunStatus.FAILED, 0, 0, 0, 0, 0, message,
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

	private SaveCounts save(String sourceCode, List<NormalizedJob> jobs, Instant now) {
		int inserted = 0;
		int updated = 0;
		for (int from = 0; from < jobs.size(); from += LOOKUP_BATCH) {
			List<NormalizedJob> batch = jobs.subList(from, Math.min(from + LOOKUP_BATCH, jobs.size()));
			Map<String, Job> existing = jobRepository
					.findBySourceCodeAndExternalIdIn(sourceCode, batch.stream().map(NormalizedJob::externalId).toList())
					.stream()
					.collect(Collectors.toMap(job -> job.getExternalId().toLowerCase(Locale.ROOT), Function.identity(),
							(a, b) -> a));
			List<Job> fresh = new ArrayList<>();
			for (NormalizedJob job : batch) {
				Job known = existing.get(job.externalId().toLowerCase(Locale.ROOT));
				if (known != null) {
					jobMapper.refresh(known, job, now);
					updated++;
				}
				else {
					fresh.add(jobMapper.toNewJob(sourceCode, job, now));
					inserted++;
				}
			}
			jobRepository.saveAll(fresh);
		}
		jobRepository.flush();
		return new SaveCounts(inserted, updated);
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

	private record SaveCounts(int inserted, int updated) {
	}

}
