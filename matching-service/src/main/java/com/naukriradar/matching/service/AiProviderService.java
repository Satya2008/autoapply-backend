package com.naukriradar.matching.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.naukriradar.common.crypto.CryptoService;
import com.naukriradar.common.exception.BadRequestException;
import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.matching.ai.ProviderConnection;
import com.naukriradar.matching.config.AiProperties;
import com.naukriradar.matching.dto.request.AiProviderCreateRequest;
import com.naukriradar.matching.dto.request.AiProviderUpdateRequest;
import com.naukriradar.matching.dto.response.AiProviderResponse;
import com.naukriradar.matching.dto.response.AiProviderTypeResponse;
import com.naukriradar.matching.model.AiProvider;
import com.naukriradar.matching.model.AiProviderType;
import com.naukriradar.matching.repository.AiProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The AI providers an admin set up: add, change, remove, reorder. Keys are encrypted at rest
 * and never returned. The router asks here, on every call, which providers to try, so a
 * change takes effect on the next AI call, on every instance.
 */
@Service
public class AiProviderService {

	private static final Logger log = LoggerFactory.getLogger(AiProviderService.class);

	private final AiProviderRepository repository;
	private final CryptoService crypto;
	private final AiProperties properties;
	private final Clock clock = Clock.systemUTC();

	public AiProviderService(AiProviderRepository repository, CryptoService crypto, AiProperties properties) {
		this.repository = repository;
		this.crypto = crypto;
		this.properties = properties;
	}

	public List<AiProviderTypeResponse> types() {
		return Arrays.stream(AiProviderType.values())
				.map(t -> new AiProviderTypeResponse(t, t.label(), t.defaultBaseUrl(), t.needsApiKey(), t.exampleModels(), t.note()))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<AiProviderResponse> list() {
		List<AiProvider> all = repository.findAllByOrderByPriorityAscNameAsc();
		return all.stream().map(p -> toResponse(p, !all.isEmpty() && all.getFirst() == p)).toList();
	}

	@Transactional(readOnly = true)
	public AiProviderResponse get(String name) {
		return toResponse(load(name), isPrimary(name));
	}

	@Transactional
	public AiProviderResponse create(AiProviderCreateRequest request, String actor) {
		if (repository.existsByName(request.name())) {
			throw new ConflictException("A provider named " + request.name() + " already exists.");
		}
		int last = repository.findAllByOrderByPriorityAscNameAsc().stream().mapToInt(AiProvider::getPriority).max().orElse(-1);
		AiProvider provider = new AiProvider(request.name(), request.type(), last + 1);
		if (request.baseUrl() != null && !request.baseUrl().isBlank()) {
			provider.setBaseUrl(trimSlash(request.baseUrl()));
		}
		provider.setModel(request.model().strip());
		provider.setStrongModel(blankToNull(request.strongModel()));
		provider.setEnabled(request.enabled() == null || request.enabled());
		if (request.timeoutSeconds() != null) {
			provider.setTimeoutSeconds(request.timeoutSeconds());
		}
		provider.setPrices(request.inputPrice(), request.outputPrice());
		setKey(provider, request.apiKey());
		provider.touch(actor, clock.instant());
		try {
			repository.saveAndFlush(provider);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("A provider named " + request.name() + " already exists.");
		}
		return toResponse(provider, isPrimary(provider.getName()));
	}

	@Transactional
	public AiProviderResponse update(String name, AiProviderUpdateRequest request, String actor) {
		AiProvider provider = load(name);
		if (request.baseUrl() != null) {
			provider.setBaseUrl(request.baseUrl().isBlank() ? provider.getType().defaultBaseUrl() : trimSlash(request.baseUrl()));
		}
		if (request.apiKey() != null) {
			setKey(provider, request.apiKey());
		}
		if (request.model() != null) {
			if (request.model().isBlank()) {
				throw new BadRequestException("model can't be blank.");
			}
			provider.setModel(request.model().strip());
		}
		if (request.strongModel() != null) {
			provider.setStrongModel(blankToNull(request.strongModel()));
		}
		if (request.enabled() != null) {
			provider.setEnabled(request.enabled());
		}
		if (request.timeoutSeconds() != null) {
			provider.setTimeoutSeconds(request.timeoutSeconds());
		}
		if (request.inputPrice() != null || request.outputPrice() != null) {
			provider.setPrices(request.inputPrice(), request.outputPrice());
		}
		provider.touch(actor, clock.instant());
		return toResponse(provider, isPrimary(name));
	}

	@Transactional
	public void delete(String name) {
		repository.delete(load(name));
	}

	/** Puts this provider first; the others keep their order behind it. */
	@Transactional
	public List<AiProviderResponse> makePrimary(String name, String actor) {
		load(name).touch(actor, clock.instant());
		return reorder(List.of(name));
	}

	/** The named providers first, in that order; the rest after them, in their old order. */
	@Transactional
	public List<AiProviderResponse> reorder(List<String> names) {
		Map<String, AiProvider> byName = new LinkedHashMap<>();
		repository.findAllByOrderByPriorityAscNameAsc().forEach(p -> byName.put(p.getName(), p));
		List<AiProvider> ordered = new ArrayList<>();
		for (String name : names) {
			AiProvider provider = byName.remove(name);
			if (provider == null) {
				throw new NotFoundException("No AI provider " + name + ".");
			}
			ordered.add(provider);
		}
		ordered.addAll(byName.values());
		for (int i = 0; i < ordered.size(); i++) {
			ordered.get(i).setPriority(i);
		}
		return list();
	}

	/**
	 * Ready providers in the order to try them, with keys decrypted.
	 *
	 * @param strong use each provider's strong model where it has one
	 */
	@Transactional(readOnly = true)
	public List<Target> targets(boolean strong) {
		return repository.findAllByOrderByPriorityAscNameAsc().stream()
				.filter(AiProvider::isReady)
				.map(p -> target(p, strong))
				.flatMap(Optional::stream)
				.toList();
	}

	/** One provider, ready or not: for testing a provider and listing its models. */
	@Transactional(readOnly = true)
	public Target target(String name) {
		AiProvider provider = load(name);
		return target(provider, false).orElseThrow(() -> new BadRequestException(
				"Provider " + name + "'s key can't be read; set it again."));
	}

	/** On the first start, adds the providers from configuration (keys from environment variables). */
	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void seedFromConfiguration() {
		if (repository.count() > 0 || properties.seedProviders().isEmpty()) {
			return;
		}
		int priority = 0;
		for (Map.Entry<String, AiProperties.SeedProvider> entry : properties.seedProviders().entrySet()) {
			AiProperties.SeedProvider seed = entry.getValue();
			AiProvider provider = new AiProvider(entry.getKey(), seed.type(), priority++);
			boolean addressGiven = seed.baseUrl() != null && !seed.baseUrl().isBlank();
			if (addressGiven) {
				provider.setBaseUrl(trimSlash(seed.baseUrl()));
			}
			provider.setModel(seed.model());
			setKey(provider, seed.apiKey());
			// switched on only when really set up: a key, or for Ollama an explicit address;
			// the rest are listed switched off, ready to be given a key
			provider.setEnabled(seed.type().needsApiKey() ? provider.getApiKey() != null : addressGiven);
			provider.touch("system", clock.instant());
			repository.save(provider);
		}
		log.info("Added {} AI provider(s) from configuration", properties.seedProviders().size());
	}

	private Optional<Target> target(AiProvider provider, boolean strong) {
		String key = null;
		if (provider.getApiKey() != null) {
			try {
				key = crypto.decrypt(provider.getApiKey(), associatedData(provider.getName()));
			}
			catch (IllegalStateException ex) {
				log.error("The key of AI provider {} can't be decrypted (encryption key changed?); skipping it",
						provider.getName());
				return Optional.empty();
			}
		}
		ProviderConnection connection = new ProviderConnection(provider.getName(), provider.getType(), provider.getBaseUrl(),
				key, Duration.ofSeconds(provider.getTimeoutSeconds()));
		String model = strong && provider.getStrongModel() != null ? provider.getStrongModel() : provider.getModel();
		// the provider's prices are for its everyday model; the strong one is priced from the list
		boolean ownPrices = model.equals(provider.getModel());
		return Optional.of(new Target(connection, model, ownPrices ? provider.getInputPrice() : null,
				ownPrices ? provider.getOutputPrice() : null));
	}

	private void setKey(AiProvider provider, String apiKey) {
		if (apiKey == null || apiKey.isBlank()) {
			provider.setApiKey(null, null);
			return;
		}
		String key = apiKey.strip();
		String hint = key.length() > 8 ? "…" + key.substring(key.length() - 4) : "…";
		provider.setApiKey(crypto.encrypt(key, associatedData(provider.getName())), hint);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	private boolean isPrimary(String name) {
		List<AiProvider> all = repository.findAllByOrderByPriorityAscNameAsc();
		return !all.isEmpty() && all.getFirst().getName().equals(name);
	}

	private AiProvider load(String name) {
		return repository.findByName(name).orElseThrow(() -> new NotFoundException("No AI provider " + name + "."));
	}

	private static String associatedData(String name) {
		return "ai-provider:" + name;
	}

	private static String trimSlash(String url) {
		String trimmed = url.strip();
		while (trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed;
	}

	private static AiProviderResponse toResponse(AiProvider p, boolean primary) {
		return new AiProviderResponse(p.getName(), p.getType(), p.getBaseUrl(), p.getModel(), p.getStrongModel(),
				p.isEnabled(), p.isReady(),
				primary, p.getPriority(), p.getApiKey() != null, p.getApiKeyHint(), p.getTimeoutSeconds(), p.getInputPrice(),
				p.getOutputPrice(), p.getUpdatedAt(), p.getUpdatedBy());
	}

	/** A provider to call, with its own prices if it has them. */
	public record Target(ProviderConnection connection, String model, BigDecimal inputPrice,
			BigDecimal outputPrice) {

		@Override
		public String toString() {
			return connection.name() + ":" + model;
		}

	}

}
