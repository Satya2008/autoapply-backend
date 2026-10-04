package com.naukriradar.core.repository;

import java.util.List;

import com.naukriradar.core.model.AuditEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, String> {

	/** Newest first; ids are time-ordered UUIDv7. Null filters match everything. */
	@Query("""
			select e from AuditEntry e
			where (:actor is null or e.actor = :actor)
			  and (:action is null or e.action = :action)
			  and (:targetType is null or e.targetType = :targetType)
			  and (:afterId is null or e.id < :afterId)
			order by e.id desc""")
	List<AuditEntry> search(@Param("actor") String actor, @Param("action") String action,
			@Param("targetType") String targetType, @Param("afterId") String afterId, Pageable page);

}
