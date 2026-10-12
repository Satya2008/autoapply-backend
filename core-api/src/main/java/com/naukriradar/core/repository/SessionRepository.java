package com.naukriradar.core.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.naukriradar.core.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionRepository extends JpaRepository<Session, String> {

	Optional<Session> findByTokenHash(String tokenHash);

	List<Session> findByUserIdAndRevokedAtIsNull(String userId);

	@Modifying
	@Query("update Session s set s.revokedAt = :at where s.userId = :userId and s.revokedAt is null")
	int revokeAll(@Param("userId") String userId, @Param("at") Instant at);

	/** Ends every live session of this user except the one holding {@code keepTokenHash} (the caller's). */
	@Modifying
	@Query("update Session s set s.revokedAt = :at where s.userId = :userId and s.revokedAt is null and s.tokenHash <> :keepTokenHash")
	int revokeAllExcept(@Param("userId") String userId, @Param("keepTokenHash") String keepTokenHash, @Param("at") Instant at);

}
