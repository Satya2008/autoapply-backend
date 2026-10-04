package com.naukriradar.matching.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.naukriradar.common.exception.ServiceUnavailableException;
import com.naukriradar.matching.ai.AiResult;
import com.naukriradar.matching.ai.AiRouter;
import com.naukriradar.matching.ai.AiUnavailableException;
import com.naukriradar.matching.dto.request.AiTestRequest;
import com.naukriradar.matching.dto.response.AiTestResponse;
import com.naukriradar.matching.dto.response.AiUsageRow;
import com.naukriradar.matching.service.AiUsageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ai")
public class AiAdminController {

	private static final String TEST_PROMPT = "ai-test";

	private final AiRouter router;
	private final AiUsageService usage;

	public AiAdminController(AiRouter router, AiUsageService usage) {
		this.router = router;
		this.usage = usage;
	}

	/**
	 * Checks keys and config with a tiny prompt; never answered from the cache, since the
	 * point is to reach the provider. 503 says what each provider answered.
	 */
	@PostMapping("/test")
	public AiTestResponse test(@Valid @RequestBody(required = false) AiTestRequest request) {
		AiTestRequest test = request == null ? new AiTestRequest(null, null, null) : request;
		Map<String, String> variables = Map.of("topic", test.topic() == null || test.topic().isBlank() ? "job hunting" : test.topic());
		AiResult result;
		try {
			result = test.provider() == null || test.provider().isBlank()
					? router.run(TEST_PROMPT, variables, null, true)
					: router.runWith(TEST_PROMPT, variables, test.provider().strip(), test.model());
		}
		catch (AiUnavailableException ex) {
			throw new ServiceUnavailableException(ex.getMessage());
		}
		return new AiTestResponse(result.provider(), result.model(), result.json(), result.tokensIn(), result.tokensOut(),
				BigDecimal.valueOf(result.costMicros()).movePointLeft(6), result.latencyMs(), result.fallbacks());
	}

	/** Spend between two instants (default: the last 30 days), grouped by provider, model, purpose, user or day. */
	@GetMapping("/usage")
	public List<AiUsageRow> usage(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
			@RequestParam(defaultValue = "provider") String groupBy) {
		return usage.report(from, to, groupBy);
	}

}
