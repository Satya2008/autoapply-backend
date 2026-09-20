package com.autoapply.matching.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, String> {
    List<PromptTemplate> findAllByOrderByNameAsc();
}
