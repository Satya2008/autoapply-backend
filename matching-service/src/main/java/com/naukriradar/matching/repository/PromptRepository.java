package com.naukriradar.matching.repository;

import java.util.List;
import java.util.Optional;

import com.naukriradar.matching.model.Prompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PromptRepository extends JpaRepository<Prompt, String> {

	Optional<Prompt> findByCodeAndActiveTrue(String code);

	Optional<Prompt> findByCodeAndVersion(String code, int version);

	List<Prompt> findByCodeOrderByVersionDesc(String code);

	List<Prompt> findAllByOrderByCodeAscVersionDesc();

	boolean existsByCode(String code);

	@Query("select coalesce(max(p.version), 0) from Prompt p where p.code = :code")
	int maxVersion(String code);

}
