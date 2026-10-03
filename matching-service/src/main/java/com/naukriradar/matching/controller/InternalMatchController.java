package com.naukriradar.matching.controller;

import java.util.List;

import com.naukriradar.matching.dto.response.MatchForApplyResponse;
import com.naukriradar.matching.service.MatchQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service API; the gateway has no route to /internal. */
@RestController
@RequestMapping("/internal/v1/users")
public class InternalMatchController {

	private final MatchQueryService queryService;

	public InternalMatchController(MatchQueryService queryService) {
		this.queryService = queryService;
	}

	@GetMapping("/{userId}/matches")
	public List<MatchForApplyResponse> matches(@PathVariable String userId,
			@RequestParam(defaultValue = "0") @Min(0) @Max(100) int minScore,
			@RequestParam(defaultValue = "200") @Min(1) @Max(500) int limit) {
		return queryService.forApply(userId, minScore, limit);
	}

}
