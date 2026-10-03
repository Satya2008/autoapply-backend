package com.naukriradar.matching.repository;

import java.util.List;
import java.util.Optional;

import com.naukriradar.matching.model.MatchRun;
import com.naukriradar.matching.model.MatchRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRunRepository extends JpaRepository<MatchRun, String> {

	Optional<MatchRun> findByIdAndUserId(String id, String userId);

	List<MatchRun> findByStatus(MatchRunStatus status);

}
