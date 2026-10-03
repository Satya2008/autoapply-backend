package com.naukriradar.job.repository;

import java.util.List;

import com.naukriradar.job.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JobRepository extends JpaRepository<Job, String> {

	long countBySourceCode(String sourceCode);

	@Query("select j.sourceCode as sourceCode, count(j) as total from Job j group by j.sourceCode")
	List<SourceJobCount> countPerSource();

	interface SourceJobCount {

		String getSourceCode();

		long getTotal();

	}

}
