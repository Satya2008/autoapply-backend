package com.naukriradar.matching.repository;

import java.util.List;
import java.util.Optional;

import com.naukriradar.matching.model.AiProvider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiProviderRepository extends JpaRepository<AiProvider, String> {

	List<AiProvider> findAllByOrderByPriorityAscNameAsc();

	Optional<AiProvider> findByName(String name);

	boolean existsByName(String name);

}
