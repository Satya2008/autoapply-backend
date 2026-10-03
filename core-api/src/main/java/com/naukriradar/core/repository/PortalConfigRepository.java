package com.naukriradar.core.repository;

import java.util.List;

import com.naukriradar.core.model.PortalConfig;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortalConfigRepository extends JpaRepository<PortalConfig, String> {

	boolean existsByDomain(String domain);

	List<PortalConfig> findByEnabledTrue();

	@EntityGraph(attributePaths = "selectors")
	List<PortalConfig> findAllByOrderByDomainAsc();

}
