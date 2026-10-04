package com.naukriradar.matching.controller;

import java.util.List;

import com.naukriradar.common.exception.ServiceUnavailableException;
import com.naukriradar.matching.ai.AiRouter;
import com.naukriradar.matching.ai.AiUnavailableException;
import com.naukriradar.matching.dto.request.AiProviderCreateRequest;
import com.naukriradar.matching.dto.request.AiProviderOrderRequest;
import com.naukriradar.matching.dto.request.AiProviderUpdateRequest;
import com.naukriradar.matching.dto.response.AiProviderResponse;
import com.naukriradar.matching.dto.response.AiProviderTypeResponse;
import com.naukriradar.matching.service.AiProviderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Which AI to use is chosen here, at runtime: add any provider with its key and model, switch
 * providers on and off, pick the primary, set the fallback order. Nothing is tied to one
 * vendor or model.
 */
@RestController
@RequestMapping("/api/v1/admin/ai")
public class AiProviderAdminController {

	private final AiProviderService providers;
	private final AiRouter router;

	public AiProviderAdminController(AiProviderService providers, AiRouter router) {
		this.providers = providers;
		this.router = router;
	}

	/** The kinds of provider that can be added, with their usual address and example models. */
	@GetMapping("/provider-types")
	public List<AiProviderTypeResponse> types() {
		return providers.types();
	}

	/** Providers in the order they are tried; the first is the primary. */
	@GetMapping("/providers")
	public List<AiProviderResponse> list() {
		return providers.list();
	}

	@GetMapping("/providers/{name}")
	public AiProviderResponse get(@PathVariable String name) {
		return providers.get(name);
	}

	@PostMapping("/providers")
	@ResponseStatus(HttpStatus.CREATED)
	public AiProviderResponse create(@Valid @RequestBody AiProviderCreateRequest request,
			@RequestHeader(value = "X-User-Id", required = false) String actor) {
		return providers.create(request, actor(actor));
	}

	/** Change the key, model, address, prices, or switch it on or off. Left-out fields stay. */
	@PatchMapping("/providers/{name}")
	public AiProviderResponse update(@PathVariable String name, @Valid @RequestBody AiProviderUpdateRequest request,
			@RequestHeader(value = "X-User-Id", required = false) String actor) {
		return providers.update(name, request, actor(actor));
	}

	@DeleteMapping("/providers/{name}")
	public ResponseEntity<Void> delete(@PathVariable String name) {
		providers.delete(name);
		return ResponseEntity.noContent().build();
	}

	/** Use this provider first; the others become its fallbacks, in their current order. */
	@PostMapping("/providers/{name}/primary")
	public List<AiProviderResponse> makePrimary(@PathVariable String name,
			@RequestHeader(value = "X-User-Id", required = false) String actor) {
		return providers.makePrimary(name, actor(actor));
	}

	/** The whole fallback order at once (drag and drop). */
	@PutMapping("/providers/order")
	public List<AiProviderResponse> reorder(@Valid @RequestBody AiProviderOrderRequest request) {
		return providers.reorder(request.names());
	}

	/** The models this provider's account can use, from the vendor: for a model dropdown. */
	@GetMapping("/providers/{name}/models")
	public List<String> models(@PathVariable String name) {
		try {
			return router.models(name);
		}
		catch (AiUnavailableException ex) {
			throw new ServiceUnavailableException(ex.getMessage());
		}
	}

	private static String actor(String header) {
		return header == null || header.isBlank() ? "admin" : header.strip();
	}

}
