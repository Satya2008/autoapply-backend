package com.naukriradar.matching.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.naukriradar.matching.model.MatchRun;
import com.naukriradar.matching.model.MatchRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchRunRepository extends JpaRepository<MatchRun, String> {

	Optional<MatchRun> findByIdAndUserId(String id, String userId);

	List<MatchRun> findByStatus(MatchRunStatus status);

	@Query("select distinct r.userId from MatchRun r where r.startedAt >= :since")
	List<String> findUsersWithRunsSince(@Param("since") Instant since);

}
