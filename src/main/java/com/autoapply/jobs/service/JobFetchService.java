package com.autoapply.jobs.service;

import com.autoapply.audit.AuditAction;
import com.autoapply.audit.AuditService;
import com.autoapply.common.AppException;
import com.autoapply.jobs.entity.Job;
import com.autoapply.jobs.repository.JobRepository;
import com.autoapply.jobs.source.JobSourceConfig;
import com.autoapply.jobs.source.JobSourceConfigRepository;
import com.autoapply.jobs.source.JobSourceProvider;
import com.autoapply.settings.SettingKeys;
import com.autoapply.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Runs enabled job sources and stores what they return. Which sources run, what they
 * search for, and how often, all come from the database.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobFetchService {

    private final JobSourceConfigRepository sourceRepository;
    private final JobRepository jobRepository;
    private final List<JobSourceProvider> providers;
    private final SettingsService settings;
    private final AuditService auditService;

    public FetchSummary fetchAll() {
        if (!settings.getBoolean(SettingKeys.JOBS_FETCH_ENABLED, true)) {
            log.info("Job fetching is disabled in settings - skipping");
            return new FetchSummary(0, 0, 0, List.of("Job fetching is disabled"));
        }

        List<JobSourceConfig> sources = sourceRepository.findByEnabledTrueOrderByPriorityAsc();
        if (sources.isEmpty()) {
            return new FetchSummary(0, 0, 0, List.of("No job sources are enabled"));
        }

        List<String> queries = settings.getList(SettingKeys.JOBS_DEFAULT_QUERIES);
        int pages = Math.max(1, settings.getInt(SettingKeys.JOBS_PAGES_PER_QUERY, 1));
        String country = settings.getString(SettingKeys.APP_DEFAULT_COUNTRY, "IN");

        int saved = 0;
        int skipped = 0;
        int failed = 0;
        List<String> messages = new ArrayList<>();

        for (JobSourceConfig source : sources) {
            List<String> sourceQueries = resolveQueries(source, queries);
            for (String query : sourceQueries) {
                for (int page = 1; page <= pages; page++) {
                    try {
                        FetchSummary result = fetchOne(source, query, country, page);
                        saved += result.saved();
                        skipped += result.skipped();
                    } catch (Exception e) {
                        failed++;
                        String message = source.getCode() + " / '" + query + "': " + e.getMessage();
                        messages.add(message);
                        log.warn("Job fetch failed for {}", message);
                        recordFailure(source, e.getMessage());
                    }
                }
            }
        }

        auditService.record("system", AuditAction.JOBS_FETCHED, "jobs", null,
                "saved=" + saved + " skipped=" + skipped + " failed=" + failed);
        log.info("Job fetch finished: {} saved, {} duplicates skipped, {} failures", saved, skipped, failed);
        return new FetchSummary(saved, skipped, failed, messages);
    }

    @Transactional
    public FetchSummary fetchOne(JobSourceConfig source, String query, String country, int page) {
        JobSourceProvider provider = providers.stream()
                .filter(p -> p.supports(source))
                .findFirst()
                .orElseThrow(() -> AppException.badRequest(
                        "No provider registered for type '" + source.getProviderType() + "'"));

        List<Job> fetched = provider.fetch(source, query, country, page);
        boolean dedup = settings.getBoolean(SettingKeys.JOBS_DEDUP_ENABLED, true);

        int saved = 0;
        int skipped = 0;
        for (Job job : fetched) {
            if (jobRepository.existsById(job.getJobId())) {
                skipped++;
                continue;
            }
            job.setFingerprint(job.computeFingerprint());
            if (dedup && jobRepository.existsByFingerprint(job.getFingerprint())) {
                skipped++;
                continue;
            }
            jobRepository.save(job);
            saved++;
        }

        source.setLastRunAt(LocalDateTime.now());
        source.setLastRunStatus("SUCCESS");
        source.setLastRunMessage("Fetched " + fetched.size() + ", saved " + saved + ", skipped " + skipped);
        source.setTotalFetched((source.getTotalFetched() == null ? 0 : source.getTotalFetched()) + saved);
        source.setConsecutiveFailures(0);
        sourceRepository.save(source);

        return new FetchSummary(saved, skipped, 0, List.of());
    }

    @Transactional
    public FetchSummary fetchByCode(String code, String query, String country, int page) {
        JobSourceConfig source = sourceRepository.findByCode(code)
                .orElseThrow(() -> AppException.notFound("Job source '" + code + "'"));
        return fetchOne(source, query, country, page);
    }

    private void recordFailure(JobSourceConfig source, String message) {
        source.setLastRunAt(LocalDateTime.now());
        source.setLastRunStatus("FAILED");
        source.setLastRunMessage(message == null ? "unknown error" : message.substring(0, Math.min(900, message.length())));
        source.setConsecutiveFailures((source.getConsecutiveFailures() == null ? 0 : source.getConsecutiveFailures()) + 1);
        sourceRepository.save(source);
    }

    private List<String> resolveQueries(JobSourceConfig source, List<String> globalQueries) {
        if (source.getDefaultQueries() != null && !source.getDefaultQueries().isBlank()) {
            return Arrays.stream(source.getDefaultQueries().split("[,\\n]"))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        return globalQueries.isEmpty() ? List.of("Software Developer") : globalQueries;
    }

    @Transactional
    public int purgeOldJobs() {
        int days = settings.getInt(SettingKeys.JOBS_RETENTION_DAYS, 60);
        int removed = jobRepository.deleteOlderThan(LocalDateTime.now().minusDays(days));
        if (removed > 0) {
            auditService.record("system", AuditAction.JOBS_PURGED, "jobs", null, removed + " jobs older than " + days + " days");
            log.info("Purged {} jobs older than {} days", removed, days);
        }
        return removed;
    }

    public record FetchSummary(int saved, int skipped, int failed, List<String> messages) {
    }
}
