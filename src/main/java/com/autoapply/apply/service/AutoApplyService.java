package com.autoapply.apply.service;
import com.autoapply.apply.entity.Application;
import com.autoapply.apply.repository.ApplicationRepository;
import com.autoapply.jobs.entity.Job;
import com.autoapply.jobs.repository.JobRepository;
import com.autoapply.matching.entity.JobMatch;
import com.autoapply.matching.repository.JobMatchRepository;
import com.autoapply.user.entity.User;
import com.autoapply.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service @RequiredArgsConstructor @Slf4j
public class AutoApplyService {
    private final ApplicationRepository applicationRepository;
    private final JobMatchRepository jobMatchRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;

    private static final int MAX_DAILY_APPLIES = 15;
    private static final int MIN_DELAY_SEC = 180;
    private static final int MAX_DELAY_SEC = 480;

    public void runAutoApplyForUser(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        if (!Boolean.TRUE.equals(user.getAutoApplyEnabled())) { log.info("Auto apply disabled for: {}", userId); return; }

        long todayCount = applicationRepository.countByUserIdAndAppliedAtAfter(userId, LocalDateTime.now().minusDays(1));
        if (todayCount >= MAX_DAILY_APPLIES) { log.info("Daily limit reached for: {}", userId); return; }

        List<JobMatch> recommended = jobMatchRepository.findByUserIdAndRecommended(userId, true);
        int applied = 0;
        int remaining = (int)(MAX_DAILY_APPLIES - todayCount);

        for (JobMatch match : recommended) {
            if (applied >= remaining) break;
            if (applicationRepository.existsByUserIdAndJobId(userId, match.getJobId())) continue;
            Job job = jobRepository.findById(match.getJobId()).orElse(null);
            if (job == null) continue;

            boolean success = applyToJob(user, job);
            Application app = Application.builder()
                    .userId(userId).jobId(job.getJobId()).jobTitle(job.getJobTitle())
                    .employerName(job.getEmployerName()).applyLink(job.getJobApplyLink())
                    .portal(job.getJobPublisher()).status(success ? "APPLIED" : "FAILED").build();
            applicationRepository.save(app);
            applied++;
            if (applied < remaining) humanDelay();
        }
        log.info("Auto apply done for {}. Applied: {}", userId, applied);
    }

    private boolean applyToJob(User user, Job job) {
        // TODO: Phase 2 - Implement Selenium based apply
        log.info("Simulating apply: {} at {}", job.getJobTitle(), job.getEmployerName());
        return true;
    }

    private void humanDelay() {
        try {
            int delay = MIN_DELAY_SEC + new Random().nextInt(MAX_DELAY_SEC - MIN_DELAY_SEC);
            Thread.sleep(delay * 1000L);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public List<Application> getUserApplications(String userId) {
        return applicationRepository.findByUserIdOrderByAppliedAtDesc(userId);
    }
}
