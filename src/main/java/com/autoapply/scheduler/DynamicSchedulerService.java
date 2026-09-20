package com.autoapply.scheduler;

import com.autoapply.apply.service.AutoApplyService;
import com.autoapply.audit.AuditAction;
import com.autoapply.audit.AuditService;
import com.autoapply.jobs.service.JobFetchService;
import com.autoapply.matching.service.MatchingService;
import com.autoapply.notification.NotificationEvent;
import com.autoapply.notification.NotificationService;
import com.autoapply.settings.SettingKeys;
import com.autoapply.settings.SettingsService;
import com.autoapply.user.entity.User;
import com.autoapply.user.service.UserService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Registers every recurring task against a cron expression read from settings, and
 * re-registers them whenever that setting changes - so schedules are edited in the
 * dashboard and applied live.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Order(10)
public class DynamicSchedulerService {

    private final ThreadPoolTaskScheduler taskScheduler;
    private final SettingsService settings;
    private final JobFetchService jobFetchService;
    private final MatchingService matchingService;
    private final AutoApplyService autoApplyService;
    private final NotificationService notificationService;
    private final UserService userService;
    private final AuditService auditService;

    private final Map<String, ScheduledFuture<?>> running = new ConcurrentHashMap<>();
    private final Map<String, TaskDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, TaskRun> lastRuns = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void registerAll() {
        define("fetch-jobs", "Fetch jobs from all enabled sources",
                SettingKeys.JOBS_FETCH_CRON, SettingKeys.JOBS_FETCH_ENABLED, this::runJobFetch);

        define("match-jobs", "Score new jobs for every active user",
                SettingKeys.JOBS_FETCH_CRON, SettingKeys.JOBS_FETCH_ENABLED, this::runMatching);

        define("auto-apply", "Apply to recommended jobs",
                SettingKeys.APPLY_CRON, SettingKeys.APPLY_ENABLED, this::runAutoApply);

        define("retry-applications", "Retry applications that previously failed",
                SettingKeys.APPLY_CRON, SettingKeys.APPLY_ENABLED, this::runRetries);

        define("cleanup-jobs", "Delete job postings past their retention window",
                SettingKeys.JOBS_CLEANUP_CRON, null, this::runJobCleanup);

        define("cleanup-audit", "Delete audit entries past their retention window",
                SettingKeys.AUDIT_CLEANUP_CRON, SettingKeys.AUDIT_ENABLED, this::runAuditCleanup);

        define("daily-digest", "Send each user their daily summary",
                SettingKeys.NOTIFY_DIGEST_CRON, SettingKeys.NOTIFY_DIGEST_ENABLED, this::runDigest);

        rescheduleAll();
    }

    private void define(String id, String description, String cronKey, String enabledKey, Runnable action) {
        definitions.put(id, new TaskDefinition(id, description, cronKey, enabledKey, action));
    }

    /** Re-reads every cron expression and rebuilds the schedule. */
    public void rescheduleAll() {
        definitions.values().forEach(this::schedule);
        log.info("Scheduler active with {} tasks", running.size());
    }

    private void schedule(TaskDefinition definition) {
        ScheduledFuture<?> existing = running.remove(definition.id());
        if (existing != null) existing.cancel(false);

        String cron = settings.getString(definition.cronKey(), null);
        if (cron == null || cron.isBlank()) {
            log.warn("Task {} has no cron expression - not scheduled", definition.id());
            return;
        }
        if (!CronExpression.isValidExpression(cron)) {
            log.warn("Task {} has an invalid cron expression '{}' - not scheduled", definition.id(), cron);
            return;
        }

        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> execute(definition), new CronTrigger(cron, TimeZone.getTimeZone(
                        settings.getString(SettingKeys.APP_TIMEZONE, "Asia/Kolkata")).toZoneId()));
        if (future != null) running.put(definition.id(), future);
    }

    private void execute(TaskDefinition definition) {
        if (definition.enabledKey() != null && !settings.getBoolean(definition.enabledKey(), true)) {
            log.debug("Task {} is disabled - skipping this tick", definition.id());
            return;
        }
        long started = System.currentTimeMillis();
        try {
            log.info("Scheduled task '{}' starting", definition.id());
            definition.action().run();
            lastRuns.put(definition.id(), new TaskRun(LocalDateTime.now(), true,
                    System.currentTimeMillis() - started, null));
        } catch (Exception e) {
            log.error("Scheduled task '{}' failed: {}", definition.id(), e.getMessage(), e);
            lastRuns.put(definition.id(), new TaskRun(LocalDateTime.now(), false,
                    System.currentTimeMillis() - started, e.getMessage()));
        }
    }

    /** Fires a task immediately, outside its schedule. */
    public String triggerNow(String taskId, String actor) {
        TaskDefinition definition = definitions.get(taskId);
        if (definition == null) throw new IllegalArgumentException("Unknown task: " + taskId);
        auditService.record(actor, AuditAction.SCHEDULE_TRIGGERED, "task", taskId, null);
        taskScheduler.execute(() -> execute(definition));
        return "Task '" + taskId + "' started";
    }

    public List<Map<String, Object>> status() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (TaskDefinition definition : definitions.values()) {
            String cron = settings.getString(definition.cronKey(), "");
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", definition.id());
            entry.put("description", definition.description());
            entry.put("cron", cron);
            entry.put("cronSettingKey", definition.cronKey());
            entry.put("enabled", definition.enabledKey() == null
                    || settings.getBoolean(definition.enabledKey(), true));
            entry.put("scheduled", running.containsKey(definition.id()));
            entry.put("nextRun", nextRun(cron));

            TaskRun last = lastRuns.get(definition.id());
            entry.put("lastRunAt", last == null ? null : last.at().toString());
            entry.put("lastRunOk", last == null ? null : last.success());
            entry.put("lastRunMillis", last == null ? null : last.millis());
            entry.put("lastRunError", last == null ? null : last.error());
            list.add(entry);
        }
        return list;
    }

    private String nextRun(String cron) {
        try {
            if (cron == null || cron.isBlank() || !CronExpression.isValidExpression(cron)) return null;
            ZonedDateTime next = CronExpression.parse(cron).next(ZonedDateTime.now());
            return next == null ? null : next.toLocalDateTime().toString();
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------- the tasks

    private void runJobFetch() {
        jobFetchService.fetchAll();
    }

    private void runMatching() {
        for (User user : userService.findAutoApplyCandidates()) {
            try {
                matchingService.matchForUser(user.getId());
            } catch (Exception e) {
                log.warn("Matching failed for {}: {}", user.getEmail(), e.getMessage());
            }
        }
    }

    private void runAutoApply() {
        autoApplyService.runForAllUsers();
    }

    private void runRetries() {
        autoApplyService.processRetries();
    }

    private void runJobCleanup() {
        jobFetchService.purgeOldJobs();
    }

    private void runAuditCleanup() {
        auditService.purgeOlderThan(settings.getInt(SettingKeys.AUDIT_RETENTION_DAYS, 180));
    }

    private void runDigest() {
        for (User user : userService.findAutoApplyCandidates()) {
            if (!Boolean.TRUE.equals(user.getNotificationsEnabled())) continue;
            notificationService.dispatch(NotificationEvent.dailyDigest(user, 0, 0, null));
        }
    }

    /** Re-applies the schedule when a cron setting is edited. */
    @EventListener
    public void onSettingChanged(SettingsService.SettingChangedEvent event) {
        boolean affectsSchedule = definitions.values().stream()
                .anyMatch(d -> d.cronKey().equals(event.key())
                        || (d.enabledKey() != null && d.enabledKey().equals(event.key())));
        if (affectsSchedule) {
            log.info("Setting '{}' changed - rebuilding schedules", event.key());
            rescheduleAll();
            auditService.record(event.updatedBy(), AuditAction.SCHEDULE_UPDATED, "schedule", event.key(), event.value());
        }
    }

    @PreDestroy
    public void shutdown() {
        running.values().forEach(future -> future.cancel(false));
        running.clear();
    }

    private record TaskDefinition(String id, String description, String cronKey,
                                  String enabledKey, Runnable action) {
    }

    private record TaskRun(LocalDateTime at, boolean success, long millis, String error) {
    }
}
