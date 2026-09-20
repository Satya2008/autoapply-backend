package com.autoapply.matching.service;

import com.autoapply.audit.AuditAction;
import com.autoapply.audit.AuditService;
import com.autoapply.jobs.entity.Job;
import com.autoapply.jobs.repository.JobRepository;
import com.autoapply.matching.ai.AiService;
import com.autoapply.matching.entity.JobMatch;
import com.autoapply.matching.repository.JobMatchRepository;
import com.autoapply.settings.SettingKeys;
import com.autoapply.settings.SettingsService;
import com.autoapply.user.entity.User;
import com.autoapply.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Two-stage matching: a cheap weighted local score filters the pool, then the configured
 * AI provider scores the survivors. Every threshold and weight is a setting.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingService {

    private final JobRepository jobRepository;
    private final JobMatchRepository jobMatchRepository;
    private final UserService userService;
    private final AiService aiService;
    private final SettingsService settings;
    private final AuditService auditService;

    @Transactional
    public MatchSummary matchForUser(String userId) {
        User user = userService.getById(userId);

        int limit = settings.getInt(SettingKeys.MATCH_MAX_JOBS_PER_RUN, 200);
        double keywordThreshold = settings.getDouble(SettingKeys.MATCH_KEYWORD_THRESHOLD, 30);
        double recommendThreshold = settings.getDouble(SettingKeys.MATCH_RECOMMEND_THRESHOLD, 70);
        boolean aiEnabled = aiService.isEnabled();

        List<Job> jobs = jobRepository.findActiveJobs();
        int evaluated = 0;
        int created = 0;
        int aiScored = 0;
        int recommended = 0;

        for (Job job : jobs) {
            if (evaluated >= limit) break;
            if (jobMatchRepository.existsByUserIdAndJobId(userId, job.getJobId())) continue;
            if (isExcluded(user, job)) continue;

            evaluated++;
            double keywordScore = scoreLocally(user, job);
            if (keywordScore < keywordThreshold) continue;

            double finalScore = keywordScore;
            String scoredBy = "KEYWORD";
            String matchingSkills = String.join(", ", overlappingSkills(user, job));
            String missingSkills = "";
            String reasoning = "Scored from skill and title overlap";

            if (aiEnabled) {
                Optional<JsonNode> aiResult = askAi(user, job);
                if (aiResult.isPresent()) {
                    JsonNode node = aiResult.get();
                    double modelScore = node.path("matchScore").asDouble(finalScore);
                    finalScore = modelScore;
                    scoredBy = "AI";
                    aiScored++;
                    matchingSkills = joinArray(node.path("matchingSkills"), matchingSkills);
                    missingSkills = joinArray(node.path("missingSkills"), "");
                    reasoning = node.path("reasoning").asText(reasoning);
                }
            }

            boolean isRecommended = finalScore >= effectiveRecommendThreshold(user, recommendThreshold);
            if (isRecommended) recommended++;

            jobMatchRepository.save(JobMatch.builder()
                    .userId(userId)
                    .jobId(job.getJobId())
                    .matchScore(round(finalScore))
                    .keywordScore(round(keywordScore))
                    .aiScore("AI".equals(scoredBy) ? round(finalScore) : null)
                    .recommended(isRecommended)
                    .matchingSkills(truncate(matchingSkills, 1900))
                    .missingSkills(truncate(missingSkills, 1900))
                    .reasoning(truncate(reasoning, 900))
                    .scoredBy(scoredBy)
                    .build());
            created++;
        }

        auditService.record(user.getEmail(), AuditAction.MATCHING_RUN, "user", userId,
                "evaluated=" + evaluated + " created=" + created + " ai=" + aiScored);
        log.info("Matching for {}: evaluated {}, created {}, AI scored {}, recommended {}",
                user.getEmail(), evaluated, created, aiScored, recommended);

        return new MatchSummary(evaluated, created, aiScored, recommended);
    }

    // ------------------------------------------------------------- scoring

    private double scoreLocally(User user, Job job) {
        double titleWeight = settings.getDouble(SettingKeys.MATCH_TITLE_WEIGHT, 25);
        double skillWeight = settings.getDouble(SettingKeys.MATCH_SKILL_WEIGHT, 35);
        double locationWeight = settings.getDouble(SettingKeys.MATCH_LOCATION_WEIGHT, 15);
        double salaryWeight = settings.getDouble(SettingKeys.MATCH_SALARY_WEIGHT, 10);
        double experienceWeight = settings.getDouble(SettingKeys.MATCH_EXPERIENCE_WEIGHT, 10);
        double recencyWeight = settings.getDouble(SettingKeys.MATCH_RECENCY_WEIGHT, 5);
        double totalWeight = titleWeight + skillWeight + locationWeight + salaryWeight + experienceWeight + recencyWeight;
        if (totalWeight <= 0) return 0;

        double score = titleWeight * titleFit(user, job)
                + skillWeight * skillFit(user, job)
                + locationWeight * locationFit(user, job)
                + salaryWeight * salaryFit(user, job)
                + experienceWeight * experienceFit(user, job)
                + recencyWeight * recencyFit(job);

        return Math.min(100, score / totalWeight * 100);
    }

    private double titleFit(User user, Job job) {
        String title = lower(job.getJobTitle());
        if (title.isEmpty()) return 0;
        List<String> targets = new ArrayList<>();
        if (user.getTargetRoles() != null) targets.addAll(user.getTargetRoles());
        if (user.getCurrentRole() != null) targets.add(user.getCurrentRole());
        if (targets.isEmpty()) return 0.5;

        for (String target : targets) {
            String needle = lower(target);
            if (needle.isEmpty()) continue;
            if (title.contains(needle)) return 1.0;
            long shared = Arrays.stream(needle.split("\\s+")).filter(title::contains).count();
            if (shared > 0) return Math.min(0.9, 0.35 + shared * 0.2);
        }
        return 0.1;
    }

    private double skillFit(User user, Job job) {
        List<String> skills = user.getSkills();
        if (skills == null || skills.isEmpty()) return 0.3;
        String haystack = lower(job.getJobTitle() + " " + job.getJobDescription() + " " + job.getJobRequiredSkills());
        long matched = skills.stream().filter(s -> !s.isBlank() && haystack.contains(lower(s))).count();
        return Math.min(1.0, (double) matched / Math.min(skills.size(), 12));
    }

    private double locationFit(User user, Job job) {
        if (Boolean.TRUE.equals(job.getJobIsRemote())) return 1.0;
        if (Boolean.TRUE.equals(user.getWillingToRelocate())) return 0.8;

        List<String> preferred = new ArrayList<>();
        if (user.getPreferredLocations() != null) preferred.addAll(user.getPreferredLocations());
        if (user.getLocation() != null) preferred.add(user.getLocation());
        if (preferred.isEmpty()) return 0.5;

        String jobLocation = lower(job.getJobCity() + " " + job.getJobState() + " " + job.getJobCountry());
        for (String location : preferred) {
            String needle = lower(location);
            if (!needle.isEmpty() && jobLocation.contains(needle)) return 1.0;
        }
        return 0.2;
    }

    private double salaryFit(User user, Job job) {
        if (user.getExpectedSalary() == null || user.getExpectedSalary() <= 0) return 0.5;
        if (job.getJobMaxSalary() == null && job.getJobMinSalary() == null) return 0.5;
        double offered = job.getJobMaxSalary() != null ? job.getJobMaxSalary() : job.getJobMinSalary();
        double expected = user.getExpectedSalary();
        if (offered >= expected) return 1.0;
        return Math.max(0, offered / expected);
    }

    private double experienceFit(User user, Job job) {
        if (user.getExperienceYears() == null) return 0.5;
        String text = lower(job.getJobTitle() + " " + job.getJobDescription());
        int years = user.getExperienceYears();
        if (text.contains("intern") || text.contains("fresher")) return years <= 1 ? 1.0 : 0.2;
        if (text.contains("senior") || text.contains("lead")) return years >= 5 ? 1.0 : 0.4;
        if (text.contains("junior") || text.contains("entry")) return years <= 3 ? 1.0 : 0.5;
        return 0.75;
    }

    private double recencyFit(Job job) {
        if (job.getJobPostedAt() == null) return 0.5;
        long days = Duration.between(job.getJobPostedAt(), LocalDateTime.now()).toDays();
        if (days <= 3) return 1.0;
        if (days <= 7) return 0.8;
        if (days <= 21) return 0.5;
        return 0.2;
    }

    private Optional<JsonNode> askAi(User user, Job job) {
        Map<String, String> variables = new HashMap<>();
        variables.put("candidateSkills", user.getSkills() == null ? "" : String.join(", ", user.getSkills()));
        variables.put("candidateExperience", String.valueOf(
                user.getExperienceYears() == null ? 0 : user.getExperienceYears()));
        variables.put("candidateRole", nullSafe(user.getCurrentRole()));
        variables.put("candidateLocation", nullSafe(user.getLocation()));
        variables.put("expectedSalary", user.getExpectedSalary() == null ? "not specified" : String.valueOf(user.getExpectedSalary()));
        variables.put("jobTitle", nullSafe(job.getJobTitle()));
        variables.put("jobCompany", nullSafe(job.getEmployerName()));
        variables.put("jobLocation", nullSafe(job.getJobCity()) + " " + nullSafe(job.getJobCountry()));
        variables.put("jobSalary", job.getJobMaxSalary() == null ? "not specified" : String.valueOf(job.getJobMaxSalary()));
        variables.put("jobDescription", truncate(nullSafe(job.getJobDescription()), 2500));
        return aiService.completeJson(AiService.PROMPT_JOB_MATCH, variables);
    }

    private boolean isExcluded(User user, Job job) {
        if (user.getExcludedCompanies() != null && job.getEmployerName() != null) {
            String employer = lower(job.getEmployerName());
            if (user.getExcludedCompanies().stream()
                    .anyMatch(c -> !c.isBlank() && employer.contains(lower(c)))) return true;
        }
        if (user.getExcludedKeywords() != null) {
            String text = lower(job.getJobTitle() + " " + job.getJobDescription());
            return user.getExcludedKeywords().stream()
                    .anyMatch(k -> !k.isBlank() && text.contains(lower(k)));
        }
        return false;
    }

    private List<String> overlappingSkills(User user, Job job) {
        if (user.getSkills() == null) return List.of();
        String haystack = lower(job.getJobTitle() + " " + job.getJobDescription() + " " + job.getJobRequiredSkills());
        return user.getSkills().stream().filter(s -> !s.isBlank() && haystack.contains(lower(s))).toList();
    }

    private double effectiveRecommendThreshold(User user, double globalThreshold) {
        return user.getMinMatchScore() != null ? user.getMinMatchScore() : globalThreshold;
    }

    // --------------------------------------------------------------- reads

    public List<JobMatch> getMatches(String userId) {
        return jobMatchRepository.findByUserIdOrderByMatchScoreDesc(userId);
    }

    public List<JobMatch> getRecommended(String userId) {
        return jobMatchRepository.findByUserIdAndRecommendedOrderByMatchScoreDesc(userId, true);
    }

    /** Matches joined with their job so the UI can render a card in one request. */
    public List<MatchView> getMatchViews(String userId, boolean recommendedOnly) {
        List<JobMatch> matches = recommendedOnly ? getRecommended(userId) : getMatches(userId);
        if (matches.isEmpty()) return List.of();

        Map<String, Job> jobsById = new HashMap<>();
        jobRepository.findAllById(matches.stream().map(JobMatch::getJobId).toList())
                .forEach(job -> jobsById.put(job.getJobId(), job));

        return matches.stream()
                .map(match -> new MatchView(match, jobsById.get(match.getJobId())))
                .filter(view -> view.job() != null)
                .toList();
    }

    public record MatchView(JobMatch match, Job job) {
    }

    // ------------------------------------------------------------- helpers

    private String joinArray(JsonNode node, String fallback) {
        if (node == null || !node.isArray() || node.isEmpty()) return fallback;
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return String.join(", ", values);
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record MatchSummary(int evaluated, int created, int aiScored, int recommended) {
    }
}
