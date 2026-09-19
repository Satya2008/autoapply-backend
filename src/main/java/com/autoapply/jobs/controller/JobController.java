package com.autoapply.jobs.controller;
import com.autoapply.jobs.entity.Job;
import com.autoapply.jobs.service.JSearchService;
import com.autoapply.jobs.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {
    private final JobRepository jobRepository;
    private final JSearchService jSearchService;

    @GetMapping
    public ResponseEntity<List<Job>> getAllJobs() {
        return ResponseEntity.ok(jobRepository.findActiveJobs());
    }

    @GetMapping("/search")
    public ResponseEntity<List<Job>> search(@RequestParam String keyword) {
        return ResponseEntity.ok(jobRepository.searchByKeyword(keyword));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Job> getById(@PathVariable String id) {
        return jobRepository.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/fetch")
    public ResponseEntity<String> triggerFetch(@RequestParam String query,
                                                @RequestParam(defaultValue = "IN") String country) {
        List<Job> jobs = jSearchService.fetchAndSaveJobs(query, country, 1);
        return ResponseEntity.ok("Fetched " + jobs.size() + " new jobs");
    }
}
