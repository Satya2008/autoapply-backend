package com.autoapply.jobs.controller;

import com.autoapply.common.ApiResponse;
import com.autoapply.common.AppException;
import com.autoapply.jobs.entity.Job;
import com.autoapply.jobs.repository.JobRepository;
import com.autoapply.jobs.service.JobFetchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Tag(name = "Jobs")
public class JobController {

    private final JobRepository jobRepository;
    private final JobFetchService jobFetchService;

    @GetMapping
    @Operation(summary = "Browse the job pool")
    public ApiResponse<Page<Job>> list(@RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(100, size),
                Sort.by("jobPostedAt").descending());
        return ApiResponse.ok(q == null || q.isBlank()
                ? jobRepository.findActiveJobs(pageable)
                : jobRepository.searchByKeyword(q, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<Job> get(@PathVariable String id) {
        return ApiResponse.ok(jobRepository.findById(id).orElseThrow(() -> AppException.notFound("Job")));
    }

    @PostMapping("/fetch")
    @Operation(summary = "Pull fresh jobs from every enabled source")
    public ApiResponse<JobFetchService.FetchSummary> fetch() {
        return ApiResponse.ok(jobFetchService.fetchAll(), "Fetch finished");
    }
}
