package com.naukriradar.job.repository;

import java.util.List;
import java.util.Optional;

import com.naukriradar.job.model.FetchRun;
import com.naukriradar.job.model.FetchRunStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FetchRunRepository extends JpaRepository<FetchRun, String> {

	@EntityGraph(attributePaths = "sources")
	Optional<FetchRun> findWithSourcesById(String id);

	List<FetchRun> findAllByOrderByStartedAtDesc(Pageable page);

	List<FetchRun> findByStatus(FetchRunStatus status);

}
