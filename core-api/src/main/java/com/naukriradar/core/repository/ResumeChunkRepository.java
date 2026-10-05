package com.naukriradar.core.repository;

import java.util.List;

import com.naukriradar.core.model.ResumeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResumeChunkRepository extends JpaRepository<ResumeChunk, String> {

	List<ResumeChunk> findByUserIdOrderByPositionAsc(String userId);

	@Modifying
	@Query("delete from ResumeChunk c where c.userId = :userId")
	int deleteByUserId(@Param("userId") String userId);

}
