package com.naukriradar.core.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.naukriradar.core.model.Application;
import com.naukriradar.core.model.ApplicationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, String> {

	Optional<Application> findByIdAndUserId(String id, String userId);

	@Query("select a.jobId from Application a where a.userId = :userId and a.jobId in :jobIds")
	Set<String> findJobIds(@Param("userId") String userId, @Param("jobIds") Collection<String> jobIds);

	long countByUserIdAndAutomatedTrueAndCreatedAtGreaterThanEqual(String userId, Instant since);

	List<Application> findByUserIdAndStatusAndNextAttemptAtLessThanEqual(String userId, ApplicationStatus status,
			Instant now);

	@Query("select distinct a.userId from Application a where a.status = :status and a.nextAttemptAt <= :now")
	List<String> findUserIdsWithStatusDue(@Param("status") ApplicationStatus status, @Param("now") Instant now);

	/** Newest first; ids are UUIDv7, so id order is creation order. */
	@Query("select a from Application a where a.userId = :userId and (:status is null or a.status = :status)"
			+ " order by a.id desc")
	List<Application> firstPage(@Param("userId") String userId, @Param("status") ApplicationStatus status, Pageable page);

	@Query("select a from Application a where a.userId = :userId and (:status is null or a.status = :status)"
			+ " and a.id < :afterId order by a.id desc")
	List<Application> pageAfter(@Param("userId") String userId, @Param("status") ApplicationStatus status,
			@Param("afterId") String afterId, Pageable page);

	List<Application> findByUserIdAndStatusOrderByMatchScoreDescIdDesc(String userId, ApplicationStatus status,
			Pageable page);

	@Query("select a.status as status, count(a) as total from Application a where a.userId = :userId group by a.status")
	List<StatusCount> countByStatus(@Param("userId") String userId);

	interface StatusCount {

		ApplicationStatus getStatus();

		long getTotal();

	}

}
