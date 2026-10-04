package com.naukriradar.core.controller;

import java.util.List;

import com.naukriradar.core.dto.response.ScheduledJobResponse;
import com.naukriradar.core.service.SchedulerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/scheduler")
public class SchedulerAdminController {

	private final SchedulerService schedulerService;

	public SchedulerAdminController(SchedulerService schedulerService) {
		this.schedulerService = schedulerService;
	}

	/** Each job: schedule, next run, whether it's running, how the last run went. */
	@GetMapping
	public List<ScheduledJobResponse> jobs() {
		return schedulerService.list();
	}

	/** Starts the job now in the background (202); 409 if it's already running. */
	@PostMapping("/{job}/run")
	public ResponseEntity<ScheduledJobResponse> run(@PathVariable String job) {
		return ResponseEntity.accepted().body(schedulerService.runNow(job));
	}

}
