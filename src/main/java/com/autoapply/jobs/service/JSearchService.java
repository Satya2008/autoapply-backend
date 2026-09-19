package com.autoapply.jobs.service;
import com.autoapply.jobs.dto.JSearchResponse;
import com.autoapply.jobs.entity.Job;
import com.autoapply.jobs.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.LocalDateTime;
import java.util.List;

@Service @RequiredArgsConstructor @Slf4j
public class JSearchService {
    private final WebClient webClient;
    private final JobRepository jobRepository;

    @Value("${jsearch.api.key}") private String apiKey;
    @Value("${jsearch.api.base-url}") private String baseUrl;
    @Value("${jsearch.api.host}") private String apiHost;
    @Value("${job.fetch.queries}") private List<String> defaultQueries;

    @Scheduled(cron = "${job.fetch.cron}")
    public void scheduledFetch() {
        log.info("Scheduled job fetch started...");
        for (String query : defaultQueries) {
            fetchAndSaveJobs(query, "IN", 1);
        }
    }

    public List<Job> fetchAndSaveJobs(String query, String country, int page) {
        try {
            JSearchResponse response = webClient.get()
                    .uri(baseUrl + "/search?query={q}&page={p}&num_pages=1&country={c}", query, page, country)
                    .header("x-rapidapi-key", apiKey)
                    .header("x-rapidapi-host", apiHost)
                    .retrieve()
                    .bodyToMono(JSearchResponse.class)
                    .block();

            if (response == null || response.getData() == null) return List.of();

            List<Job> jobs = response.getData().stream()
                    .map(this::mapToJob)
                    .filter(job -> !jobRepository.existsById(job.getJobId()))
                    .toList();
            return jobRepository.saveAll(jobs);
        } catch (Exception e) {
            log.error("JSearch error for {}: {}", query, e.getMessage());
            return List.of();
        }
    }

    private Job mapToJob(JSearchResponse.JSearchJob j) {
        return Job.builder()
                .jobId(j.getJobId()).jobTitle(j.getJobTitle()).jobPublisher(j.getJobPublisher())
                .employerName(j.getEmployerName()).employerLogo(j.getEmployerLogo())
                .employerWebsite(j.getEmployerWebsite()).jobApplyLink(j.getJobApplyLink())
                .jobApplyIsDirect(j.getJobApplyIsDirect()).jobDescription(j.getJobDescription())
                .jobIsRemote(j.getJobIsRemote()).jobCity(j.getJobCity()).jobState(j.getJobState())
                .jobCountry(j.getJobCountry()).jobEmploymentType(j.getJobEmploymentType())
                .jobMinSalary(j.getJobMinSalary()).jobMaxSalary(j.getJobMaxSalary())
                .jobSalaryCurrency(j.getJobSalaryCurrency()).jobSalaryPeriod(j.getJobSalaryPeriod())
                .jobRequiredSkills(j.getJobRequiredSkills()).jobPostedAt(LocalDateTime.now()).build();
    }
}
