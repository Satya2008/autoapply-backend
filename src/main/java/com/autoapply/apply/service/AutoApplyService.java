package com.autoapply.apply.service;

import com.autoapply.apply.engine.ApplyOutcome;
import com.autoapply.apply.engine.BrowserApplyEngine;
import com.autoapply.apply.entity.Application;
import com.autoapply.apply.repository.ApplicationRepository;
import com.autoapply.apply.risk.ApplyRiskAssessor;
import com.autoapply.audit.AuditAction;
import com.autoapply.audit.AuditService;
import com.autoapply.jobs.entity.Job;
import com.autoapply.jobs.repository.JobRepository;
import com.autoapply.matching.ai.AiService;
import com.autoapply.matching.entity.JobMatch;
import com.autoapply.matching.repository.JobMatchRepository;
import com.autoapply.notification.NotificationEvent;
import com.autoapply.notification.NotificationService;
import com.autoapply.settings.SettingKeys;
import com.autoapply.settings.SettingsService;
import com.autoapply.user.entity.User;
import com.autoapply.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Decides which matched jobs to apply to and drives the apply engine. Daily caps,
 * pacing, working hours and whether a real browser is used are all settings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoApplyService {

    private final ApplicationRepository applicationRepository;
    private final JobMatchRepository jobMatchRepository;
    private final JobRepository jobRepository;
    private final UserService userService;
    private final BrowserApplyEngine browserEngine;
    private final ApplyRiskAssessor riskAssessor;
    private final PrefillService prefillService;
    private final AiService aiService;
    private final NotificationService notificationService;
    private final SettingsService settings;
    private final AuditService auditService;
    private final Random random = new Random();

    public RunSummary runForAllUsers() {
        if (!settings.getBoolean(SettingKeys.APPLY_ENABLED, true)) {
            return new RunSummary(0, 0, 0, 0, "Auto apply is disabled in settings");
        }
        if (!isWithinWorkingWindow()) {
            return new RunSummary(0, 0, 0, 0, "Outside the configured working window");
        }

        List<User> candidates = userService.findAutoApplyCandidates();
        int applied = 0;
        int queued = 0;
        int failed = 0;
        int skipped = 0;

        for (User user : candidates) {
            RunSummary summary = runForUser(user.getId());
            applied += summary.applied();
            queued += summary.queued();
            failed += summary.failed();
            skipped += summary.skipped();
        }

        log.info("Auto apply sweep across {} users: {} applied, {} queued for the candidate, "
                + "{} failed, {} skipped", candidates.size(), applied, queued, failed, skipped);
        return new RunSummary(applied, queued, failed, skipped,
                "Processed " + candidates.size() + " users");
    }

    @Transactional
    public RunSummary runForUser(String userId) {
        User user = userService.getById(userId);

        if (!settings.getBoolean(SettingKeys.APPLY_ENABLED, true)) {
            return new RunSummary(0, 0, 0, 0, "Auto apply is disabled in settings");
        }
        if (!Boolean.TRUE.equals(user.getAutoApplyEnabled())) {
            return new RunSummary(0, 0, 0, 0, "Auto apply is switched off for this user");
        }

        int dailyLimit = user.getDailyApplyLimit() != null
                ? user.getDailyApplyLimit()
                : settings.getInt(SettingKeys.APPLY_MAX_DAILY, 15);
        long alreadyToday = applicationRepository.countByUserIdAndAppliedAtAfter(
                userId, LocalDateTime.now().minusDays(1));
        int remaining = (int) (dailyLimit - alreadyToday);
        if (remaining <= 0) {
            return new RunSummary(0, 0, 0, 0, "Daily limit of " + dailyLimit + " already reached");
        }

        double minScore = user.getMinMatchScore() != null
                ? user.getMinMatchScore()
                : settings.getDouble(SettingKeys.APPLY_MIN_SCORE, 70);
        boolean useBrowser = "BROWSER".equalsIgnoreCase(settings.getString(SettingKeys.APPLY_MODE, "SIMULATE"));

        List<JobMatch> queue = jobMatchRepository.findApplicable(userId, minScore);
        int applied = 0;
        int queued = 0;
        int failed = 0;
        int skipped = 0;

        for (JobMatch match : queue) {
            if (applied + failed >= remaining) break;
            if (applicationRepository.existsByUserIdAndJobId(userId, match.getJobId())) {
                skipped++;
                continue;
            }
            Job job = jobRepository.findById(match.getJobId()).orElse(null);
            if (job == null) {
                skipped++;
                continue;
            }

            Application application = submit(user, job, match, useBrowser);
            switch (application.getStatus()) {
                case "APPLIED", "DRY_RUN" -> applied++;
                case "NEEDS_YOU" -> queued++;
                case "FAILED", "RETRY_SCHEDULED" -> failed++;
                default -> skipped++;
            }

            match.setStatus("APPLIED");
            match.setAppliedAt(LocalDateTime.now());
            jobMatchRepository.save(match);

            if (applied + failed < remaining && !"NEEDS_YOU".equals(application.getStatus())) humanPause();
        }

        auditService.record(user.getEmail(), AuditAction.AUTO_APPLY_RUN, "user", userId,
                "applied=" + applied + " queued=" + queued + " failed=" + failed + " skipped=" + skipped);
        return new RunSummary(applied, queued, failed, skipped, "Completed for " + user.getEmail());
    }

    @Transactional
    public Application submit(User user, Job job, JobMatch match, boolean useBrowser) {
        String coverLetter = generateCoverLetter(user, job);
        ApplyRiskAssessor.Assessment risk = riskAssessor.assess(job.getJobApplyLink());

        Application application = Application.builder()
                .userId(user.getId())
                .jobId(job.getJobId())
                .jobTitle(job.getJobTitle())
                .employerName(job.getEmployerName())
                .applyLink(job.getJobApplyLink())
                .portal(job.getJobPublisher())
                .portalCode(risk.portal() == null ? null : risk.portal().getCode())
                .matchScore(match == null ? null : match.getMatchScore())
                .coverLetter(coverLetter)
                .botRisk(risk.risk())
                .riskReason(risk.reason())
                .attemptCount(1)
                .build();

        if (!useBrowser) {
            application.setStatus("APPLIED");
            application.setSubmittedVia("SIMULATED");
            application.setMessage("Recorded in simulate mode - no browser was used");

        } else if (riskAssessor.canAutomate(risk)) {
            ApplyOutcome outcome = browserEngine.apply(user, job, coverLetter);
            application.setSubmittedVia("AUTO");
            application.setMessage(outcome.message());
            application.setScreenshotPath(outcome.screenshotPath());
            application.setStatus(switch (outcome.status()) {
                case SUCCESS -> "APPLIED";
                case DRY_RUN -> "DRY_RUN";
                case SKIPPED -> "SKIPPED";
                case FAILED -> scheduleRetryIfAllowed(application);
            });

        } else {
            // The site blocks automation. Prepare every answer and hand it to the candidate.
            application.setStatus(settings.getBoolean(SettingKeys.APPLY_ASSIST_QUEUE_ENABLED, true)
                    ? "NEEDS_YOU" : "SKIPPED");
            application.setSubmittedVia("ASSISTED");
            application.setPrefillJson(prefillService.build(user, job, coverLetter));
            application.setMessage(risk.reason()
                    + ". Everything is filled in for you - open the posting and paste.");
        }

        Application saved = applicationRepository.save(application);

        if (saved.getStatus().equals("APPLIED") || saved.getStatus().equals("DRY_RUN")) {
            auditService.record(user.getEmail(), AuditAction.APPLICATION_SUBMITTED, "job", job.getJobId(),
                    job.getJobTitle() + " at " + job.getEmployerName());
            if (settings.getBoolean(SettingKeys.NOTIFY_ON_APPLY, true)) {
                notificationService.dispatch(NotificationEvent.applicationSubmitted(user, job, saved));
            }
        } else if (saved.getStatus().equals("FAILED") || saved.getStatus().equals("RETRY_SCHEDULED")) {
            auditService.recordFailure(user.getEmail(), AuditAction.APPLICATION_FAILED, "job", job.getJobId(),
                    saved.getMessage());
        }
        return saved;
    }

    private String scheduleRetryIfAllowed(Application application) {
        int maxRetries = settings.getInt(SettingKeys.APPLY_MAX_RETRIES, 3);
        if (application.getAttemptCount() >= maxRetries) return "FAILED";
        application.setNextRetryAt(LocalDateTime.now()
                .plusMinutes(settings.getInt(SettingKeys.APPLY_RETRY_BACKOFF_MINUTES, 30)));
        return "RETRY_SCHEDULED";
    }

    @Transactional
    public int processRetries() {
        List<Application> due = applicationRepository.findDueForRetry(LocalDateTime.now());
        if (due.isEmpty()) return 0;

        boolean useBrowser = "BROWSER".equalsIgnoreCase(settings.getString(SettingKeys.APPLY_MODE, "SIMULATE"));
        int retried = 0;

        for (Application application : due) {
            try {
                User user = userService.getById(application.getUserId());
                Job job = jobRepository.findById(application.getJobId()).orElse(null);
                if (job == null) {
                    application.setStatus("FAILED");
                    application.setMessage("The job posting is no longer available");
                    applicationRepository.save(application);
                    continue;
                }

                application.setAttemptCount(application.getAttemptCount() + 1);
                if (useBrowser) {
                    ApplyOutcome outcome = browserEngine.apply(user, job, application.getCoverLetter());
                    application.setMessage(outcome.message());
                    application.setScreenshotPath(outcome.screenshotPath());
                    application.setStatus(outcome.isSuccessful() ? "APPLIED" : scheduleRetryIfAllowed(application));
                } else {
                    application.setStatus("APPLIED");
                    application.setMessage("Recorded in simulate mode on retry");
                }
                applicationRepository.save(application);
                auditService.record("system", AuditAction.APPLICATION_RETRIED, "application",
                        application.getId(), application.getStatus());
                retried++;
            } catch (Exception e) {
                log.warn("Retry failed for application {}: {}", application.getId(), e.getMessage());
            }
        }
        return retried;
    }

    private String generateCoverLetter(User user, Job job) {
        if (!aiService.isEnabled()) {
            return user.getCoverLetterTemplate();
        }
        Map<String, String> variables = new HashMap<>();
        variables.put("candidateName", nullSafe(user.getFullName()));
        variables.put("candidateRole", nullSafe(user.getCurrentRole()));
        variables.put("candidateExperience", String.valueOf(
                user.getExperienceYears() == null ? 0 : user.getExperienceYears()));
        variables.put("candidateSkills", user.getSkills() == null ? "" : String.join(", ", user.getSkills()));
        variables.put("jobTitle", nullSafe(job.getJobTitle()));
        variables.put("jobCompany", nullSafe(job.getEmployerName()));
        variables.put("jobDescription", truncate(nullSafe(job.getJobDescription()), 2000));
        return aiService.complete(AiService.PROMPT_COVER_LETTER, variables)
                .orElse(user.getCoverLetterTemplate());
    }

    private boolean isWithinWorkingWindow() {
        if (!settings.getBoolean(SettingKeys.APPLY_WORKING_HOURS_ONLY, true)) return true;
        LocalDateTime now = LocalDateTime.now();
        if (settings.getBoolean(SettingKeys.APPLY_SKIP_WEEKENDS, true)
                && (now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY)) {
            return false;
        }
        int hour = now.getHour();
        return hour >= settings.getInt(SettingKeys.APPLY_WORKING_HOUR_START, 9)
                && hour < settings.getInt(SettingKeys.APPLY_WORKING_HOUR_END, 19);
    }

    private void humanPause() {
        int min = settings.getInt(SettingKeys.APPLY_MIN_DELAY_SECONDS, 180);
        int max = Math.max(min + 1, settings.getInt(SettingKeys.APPLY_MAX_DELAY_SECONDS, 480));
        int seconds = min + random.nextInt(max - min);
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public List<Application> getUserApplications(String userId) {
        return applicationRepository.findByUserIdOrderByAppliedAtDesc(userId);
    }

    // --------------------------------------------------- assisted applications

    /** Roles the engine will not submit, with every answer already prepared. */
    public List<AssistedApplication> getAssistedQueue(String userId) {
        return applicationRepository.findByUserIdAndStatusOrderByMatchScoreDesc(userId, "NEEDS_YOU")
                .stream()
                .map(application -> new AssistedApplication(
                        application, prefillService.parse(application.getPrefillJson())))
                .toList();
    }

    @Transactional
    public Application markApplied(String userId, String applicationId) {
        Application application = requireOwned(userId, applicationId);
        application.setStatus("APPLIED");
        application.setMessage("Submitted by you on the site");
        application.setSubmittedVia("ASSISTED");
        Application saved = applicationRepository.save(application);
        auditService.record(userService.getById(userId).getEmail(),
                AuditAction.APPLICATION_SUBMITTED, "application", applicationId, "assisted");
        return saved;
    }

    @Transactional
    public Application skipAssisted(String userId, String applicationId) {
        Application application = requireOwned(userId, applicationId);
        application.setStatus("SKIPPED");
        application.setMessage("You chose to skip this one");
        return applicationRepository.save(application);
    }

    private Application requireOwned(String userId, String applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> com.autoapply.common.AppException.notFound("Application"));
        if (!application.getUserId().equals(userId)) {
            throw com.autoapply.common.AppException.forbidden("That application belongs to someone else");
        }
        return application;
    }

    public record AssistedApplication(Application application, List<Map<String, Object>> prefill) {
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record RunSummary(int applied, int queued, int failed, int skipped, String message) {
    }
}
