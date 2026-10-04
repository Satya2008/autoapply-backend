package com.naukriradar.matching.controller;

import java.math.BigDecimal;
import java.util.Map;

import com.naukriradar.matching.ai.AiResult;
import com.naukriradar.matching.ai.AiRouter;
import com.naukriradar.matching.ai.AiUnavailableException;
import com.naukriradar.matching.dto.request.AiRunRequest;
import com.naukriradar.matching.dto.response.AiRunResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI for the other services: they send a prompt code and its variables, and get the checked
 * answer. Providers, fallback, caching, budgets and cost tracking all stay in one place.
 * Not routed by the gateway.
 */
@RestController
@RequestMapping("/internal/v1/ai")
public class InternalAiController {

	private final AiRouter router;

	public InternalAiController(AiRouter router) {
		this.router = router;
	}

	/**
	 * When AI is off, the user's budget is spent or no provider answered, the reply says so
	 * with a reason (still 200): the caller carries on without AI, and doesn't retry an
	 * answer that would cost money again.
	 */
	@PostMapping("/run")
	public AiRunResponse run(@Valid @RequestBody AiRunRequest request) {
		AiResult result;
		try {
			result = router.run(request.prompt(), request.variables() == null ? Map.of() : request.variables(),
					request.userId(), request.freshAnswer());
		}
		catch (AiUnavailableException ex) {
			return AiRunResponse.unavailable(ex.getMessage());
		}
		return new AiRunResponse(result.provider(), result.model(), result.json(),
				BigDecimal.valueOf(result.costMicros()).movePointLeft(6), null);
	}

}
