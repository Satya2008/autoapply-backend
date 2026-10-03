package com.naukriradar.core.repository;

import java.util.Optional;

import com.naukriradar.core.model.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRepository extends JpaRepository<Resume, String> {

	Optional<Resume> findByUserId(String userId);

}
