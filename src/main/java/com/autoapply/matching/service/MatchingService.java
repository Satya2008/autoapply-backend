package com.autoapply.matching.service;
import com.autoapply.jobs.entity.Job;
import com.autoapply.jobs.repository.JobRepository;
import com.autoapply.matching.entity.JobMatch;
import com.autoapply.matching.repository.JobMatchRepository;
import com.autoapply.user.entity.User;
import com.autoapply.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class MatchingService {
    private final JobRepository jobRepository;
    private final JobMatchRepository jobMatchRepository;
    private final UserRepository userRepository;
    private final GeminiService geminiService;

    public List<JobMatch> matchJobsForUser(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        List<Job> allJobs = jobRepository.findActiveJobs();
        List<JobMatch> matches = new ArrayList<>();

        for (Job job : allJobs) {
            if (jobMatchRepository.existsByUserIdAndJobId(userId, job.getJobId())) continue;
            double quickScore = calculateKeywordScore(user.getSkills(), job);

            if (quickScore >= 40) {
                double finalScore = geminiService.getMatchScore(user, job);
                JobMatch match = JobMatch.builder()
                        .userId(userId).jobId(job.getJobId()).matchScore(finalScore)
                        .recommended(finalScore >= 70).status("MATCHED").build();
                matches.add(jobMatchRepository.save(match));
            }
        }
        return matches;
    }

    private double calculateKeywordScore(List<String> skills, Job job) {
        if (skills == null || skills.isEmpty()) return 0;
        String jobText = (job.getJobTitle() + " " + job.getJobDescription()).toLowerCase();
        long matched = skills.stream().filter(s -> jobText.contains(s.toLowerCase())).count();
        return (double) matched / skills.size() * 100;
    }

    public List<JobMatch> getMatchesForUser(String userId) {
        return jobMatchRepository.findByUserIdOrderByMatchScoreDesc(userId);
    }
    public List<JobMatch> getRecommendedForUser(String userId) {
        return jobMatchRepository.findByUserIdAndRecommended(userId, true);
    }
}
