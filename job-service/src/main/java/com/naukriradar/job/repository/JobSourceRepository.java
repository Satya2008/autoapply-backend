package com.naukriradar.job.repository;

import java.util.List;
import java.util.Optional;

import com.naukriradar.job.model.JobSource;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobSourceRepository extends JpaRepository<JobSource, String> {

	Optional<JobSource> findByCode(String code);

	/** Loads the source with its maps, so it can be used after the transaction ends. */
	@EntityGraph(attributePaths = { "headers", "queryParams", "fieldMappings" })
	Optional<JobSource> findWithConfigById(String id);

	boolean existsByCode(String code);

	List<JobSource> findAllByOrderByPriorityDescCodeAsc();

	List<JobSource> findByEnabledTrueOrderByPriorityDescCodeAsc();

}
