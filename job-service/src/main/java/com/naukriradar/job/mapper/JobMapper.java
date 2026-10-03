package com.naukriradar.job.mapper;

import java.time.Instant;

import com.naukriradar.job.dto.response.JobPreview;
import com.naukriradar.job.model.Job;
import com.naukriradar.job.normalizer.NormalizedJob;
import org.springframework.stereotype.Component;

@Component
public class JobMapper {

	public Job toNewJob(String sourceCode, NormalizedJob normalized, Instant now) {
		Job job = new Job(sourceCode, normalized.externalId(), now);
		refresh(job, normalized, now);
		return job;
	}

	public void refresh(Job job, NormalizedJob normalized, Instant now) {
		job.refresh(normalized.title(), normalized.company(), normalized.location(), normalized.remote(),
				normalized.salaryMin(), normalized.salaryMax(), normalized.currency(), normalized.postedAt(),
				normalized.applyUrl(), normalized.description(), now);
	}

	public JobPreview toPreview(NormalizedJob job) {
		return new JobPreview(job.externalId(), job.title(), job.company(), job.location(), job.remote(),
				job.salaryMin(), job.salaryMax(), job.currency(), job.postedAt(), job.applyUrl());
	}

}
