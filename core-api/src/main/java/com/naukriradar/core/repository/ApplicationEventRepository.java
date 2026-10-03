package com.naukriradar.core.repository;

import java.util.List;

import com.naukriradar.core.model.ApplicationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationEventRepository extends JpaRepository<ApplicationEvent, String> {

	List<ApplicationEvent> findByApplicationIdOrderByAtAscIdAsc(String applicationId);

}
