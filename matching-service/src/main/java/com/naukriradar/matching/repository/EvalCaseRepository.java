package com.naukriradar.matching.repository;

import java.util.List;

import com.naukriradar.matching.model.EvalCase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalCaseRepository extends JpaRepository<EvalCase, String> {

	List<EvalCase> findAllByOrderByNameAsc();

	boolean existsByName(String name);

}
