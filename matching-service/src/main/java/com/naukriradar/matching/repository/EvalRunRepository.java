package com.naukriradar.matching.repository;

import java.util.List;

import com.naukriradar.matching.model.EvalKind;
import com.naukriradar.matching.model.EvalRun;
import com.naukriradar.matching.model.EvalRunStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalRunRepository extends JpaRepository<EvalRun, String> {

	List<EvalRun> findAllByOrderByCreatedAtDesc(Pageable page);

	boolean existsByKindAndPromptCodeAndPromptVersionAndStatusAndPassedTrue(EvalKind kind, String promptCode,
			Integer promptVersion, EvalRunStatus status);

}
