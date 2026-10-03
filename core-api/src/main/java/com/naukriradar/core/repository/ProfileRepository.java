package com.naukriradar.core.repository;

import java.util.Optional;

import com.naukriradar.core.model.Profile;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface ProfileRepository extends JpaRepository<Profile, String> {

	/**
	 * Loads the profile with SELECT ... FOR UPDATE. Anyone else asking for the same row waits
	 * until this transaction ends, which serialises apply planning per user: two planners
	 * can't both see "not applied yet" and apply twice.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
	@Query("select p from Profile p join fetch p.user where p.userId = :userId")
	Optional<Profile> findForUpdate(@Param("userId") String userId);

}
