package com.naukriradar.matching.repository;

import java.time.Instant;

import com.naukriradar.matching.model.AiUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiUsageRepository extends JpaRepository<AiUsage, String> {

	@Query("select coalesce(sum(u.costMicros), 0) from AiUsage u where u.userId = :userId and u.at >= :since")
	long costSince(String userId, Instant since);

}
