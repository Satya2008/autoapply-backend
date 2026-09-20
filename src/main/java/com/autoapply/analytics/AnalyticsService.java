package com.autoapply.analytics;

import com.autoapply.apply.repository.ApplicationRepository;
import com.autoapply.audit.AuditLogRepository;
import com.autoapply.jobs.repository.JobRepository;
import com.autoapply.jobs.source.JobSourceConfigRepository;
import com.autoapply.matching.repository.JobMatchRepository;
import com.autoapply.user.entity.Role;
import com.autoapply.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final JobMatchRepository jobMatchRepository;
    private final ApplicationRepository applicationRepository;
    private final JobSourceConfigRepository jobSourceRepository;
    private final AuditLogRepository auditLogRepository;

    public Map<String, Object> overview() {
        LocalDateTime dayAgo = LocalDateTime.now().minusDays(1);
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("activeUsers", userRepository.countByEnabledTrue());
        stats.put("admins", userRepository.countByRole(Role.ADMIN) + userRepository.countByRole(Role.SUPER_ADMIN));
        stats.put("newUsersThisWeek", userRepository.countByCreatedAtAfter(weekAgo));

        stats.put("totalJobs", jobRepository.count());
        stats.put("jobsToday", jobRepository.countByFetchedAtAfter(dayAgo));

        stats.put("totalMatches", jobMatchRepository.count());
        stats.put("recommendedMatches", jobMatchRepository.countByRecommendedTrue());
        stats.put("matchesToday", jobMatchRepository.countByMatchedAtAfter(dayAgo));
        Double average = jobMatchRepository.averageScore();
        stats.put("averageMatchScore", average == null ? 0 : Math.round(average * 10) / 10.0);

        long total = applicationRepository.count();
        long applied = applicationRepository.countByStatus("APPLIED");
        stats.put("totalApplications", total);
        stats.put("applicationsToday", applicationRepository.countByAppliedAtAfter(dayAgo));
        stats.put("applicationsThisWeek", applicationRepository.countByAppliedAtAfter(weekAgo));
        stats.put("successRate", total == 0 ? 0 : Math.round((double) applied / total * 1000) / 10.0);

        stats.put("jobSources", jobSourceRepository.count());
        stats.put("enabledJobSources", jobSourceRepository.findByEnabledTrueOrderByPriorityAsc().size());
        stats.put("auditEntries", auditLogRepository.count());
        return stats;
    }

    /** Applications per day for the last N days, zero-filled so charts have no gaps. */
    public List<Map<String, Object>> applicationsTimeline(int days) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : applicationRepository.countPerDaySince(LocalDateTime.now().minusDays(days))) {
            counts.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }

        List<Map<String, Object>> timeline = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            String key = date.toString();
            timeline.add(Map.of(
                    "date", key,
                    "label", date.getDayOfMonth() + "/" + date.getMonthValue(),
                    "count", counts.getOrDefault(key, 0L)));
        }
        return timeline;
    }

    public List<Map<String, Object>> applicationsByStatus() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : applicationRepository.countGroupedByStatus()) {
            result.add(Map.of(
                    "status", row[0] == null ? "UNKNOWN" : row[0],
                    "count", ((Number) row[1]).longValue()));
        }
        return result;
    }

    public List<Map<String, Object>> jobsBySource() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : jobRepository.countGroupedBySource()) {
            result.add(Map.of(
                    "source", row[0] == null ? "unknown" : row[0],
                    "count", ((Number) row[1]).longValue()));
        }
        result.sort((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")));
        return result;
    }

    public Map<String, Object> userStats(String userId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        List<com.autoapply.apply.entity.Application> applications =
                applicationRepository.findByUserIdOrderByAppliedAtDesc(userId);

        long applied = applications.stream()
                .filter(a -> "APPLIED".equals(a.getStatus()) || "DRY_RUN".equals(a.getStatus())).count();

        stats.put("totalMatches", jobMatchRepository.countByUserId(userId));
        stats.put("recommended", jobMatchRepository.findByUserIdAndRecommendedOrderByMatchScoreDesc(userId, true).size());
        stats.put("totalApplications", applications.size());
        stats.put("successfulApplications", applied);
        stats.put("appliedToday", applicationRepository.countByUserIdAndAppliedAtAfter(
                userId, LocalDateTime.now().minusDays(1)));
        stats.put("successRate", applications.isEmpty() ? 0
                : Math.round((double) applied / applications.size() * 1000) / 10.0);
        return stats;
    }
}
