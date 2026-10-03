package com.naukriradar.job.mapper;

import com.naukriradar.job.dto.response.JobPreview;
import com.naukriradar.job.normalizer.NormalizedJob;
import org.springframework.stereotype.Component;

@Component
public class JobMapper {

	public JobPreview toPreview(NormalizedJob job) {
		return new JobPreview(job.externalId(), job.title(), job.company(), job.location(), job.remote(),
				job.salaryMin(), job.salaryMax(), job.currency(), job.postedAt(), job.applyUrl());
	}

}
