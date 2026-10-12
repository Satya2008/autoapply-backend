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

}
