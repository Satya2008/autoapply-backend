package com.naukriradar.matching.embedding;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.naukriradar.common.resilience.Resilience;
import com.naukriradar.matching.ai.AiClient;
import com.naukriradar.matching.ai.AiEmbeddings;
import com.naukriradar.matching.config.AiProperties;
import com.naukriradar.matching.model.AiProviderType;
import com.naukriradar.matching.repository.EmbeddingStore;
import com.naukriradar.matching.service.AiProviderService;
import com.naukriradar.matching.service.AiUsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Texts to vectors, with whichever embedding model the admin picked (the first ready provider
 * with an embedding model), else the built-in local embedder. Paid vectors are cached by text
 * hash, so the same text is paid for once. No vendor or model is named here.
 *
 * <p>Never a dependency: if the chosen provider fails, the local embedder answers instead,
 * for the whole call, so every vector in one answer comes from the same model.
 */
@Service
public class EmbeddingService {

	private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

	/** Longer texts are cut: embedding models have input limits, and the start says the most. */
	static final int MAX_CHARS = 8000;

	/** Texts per provider call. */
	static final int BATCH = 64;

	static final String PURPOSE = "embedding";

	private final AiProviderService providers;
	private final Map<AiProviderType, AiClient> clients = new EnumMap<>(AiProviderType.class);
	private final HashingEmbedder local;
	private final EmbeddingStore store;
	private final Resilience resilience;
	private final AiUsageService usage;
	private final AiProperties properties;
	private final Clock clock = Clock.systemUTC();

	public EmbeddingService(AiProviderService providers, List<AiClient> clients, HashingEmbedder local, EmbeddingStore store,
			Resilience resilience, AiUsageService usage, AiProperties properties) {
		this.providers = providers;
		clients.forEach(client -> this.clients.put(client.type(), client));
		this.local = local;
		this.store = store;
		this.resilience = resilience;
		this.usage = usage;
		this.properties = properties;
	}

	/** The model new vectors come from right now. */
	public String currentModelKey() {
		return remoteTarget().map(EmbeddingService::key).orElse(HashingEmbedder.MODEL_KEY);
	}

	/** With the current model, or the local one if that fails. */
	public Embeddings embed(List<String> texts) {
		Optional<AiProviderService.Target> target = remoteTarget();
		if (target.isPresent()) {
			try {
				return new Embeddings(key(target.get()), remote(target.get(), texts), false);
			}
			catch (EmbeddingUnavailableException ex) {
				log.warn("Embedding with {} failed, using the local embedder: {}", target.get(), ex.getMessage());
				return new Embeddings(HashingEmbedder.MODEL_KEY, local(texts), true);
			}
		}
		return new Embeddings(HashingEmbedder.MODEL_KEY, local(texts), false);
	}

	/**
	 * With exactly this model: for adding vectors next to ones already made with it.
	 *
	 * @throws EmbeddingUnavailableException if that model isn't the current one any more, or its provider failed
	 */
	public List<float[]> embed(List<String> texts, String modelKey) {
		if (HashingEmbedder.MODEL_KEY.equals(modelKey)) {
			return local(texts);
		}
		AiProviderService.Target target = remoteTarget().filter(t -> key(t).equals(modelKey))
				.orElseThrow(() -> new EmbeddingUnavailableException("The embedding model " + modelKey + " isn't set up any more."));
		return remote(target, texts);
	}

	/** "openai:text-embedding-3-small": the same model through two accounts gives the same vectors. */
	static String key(AiProviderService.Target target) {
		return target.connection().type().name().toLowerCase(Locale.ROOT) + ":" + target.model();
	}

	private Optional<AiProviderService.Target> remoteTarget() {
		if (!properties.enabled()) {
			return Optional.empty();
		}
		return providers.embeddingTarget();
	}

	private List<float[]> local(List<String> texts) {
		return texts.stream().map(text -> local.embed(clip(text))).toList();
	}

	private List<float[]> remote(AiProviderService.Target target, List<String> texts) {
		String modelKey = key(target);
		// distinct texts by hash, in first-seen order
		Map<String, String> byHash = new LinkedHashMap<>();
		List<String> hashes = new ArrayList<>(texts.size());
		for (String text : texts) {
			String clipped = clip(text);
			if (clipped.isEmpty()) {
				// vendors refuse empty input; an empty text is similar to nothing anyway
				hashes.add(null);
				continue;
			}
			String hash = Vectors.hash(clipped);
			hashes.add(hash);
			byHash.putIfAbsent(hash, clipped);
		}
		Map<String, float[]> vectors = new LinkedHashMap<>(store.cached(modelKey, byHash.keySet()));
		List<String> missing = byHash.keySet().stream().filter(hash -> !vectors.containsKey(hash)).toList();
		for (int from = 0; from < missing.size(); from += BATCH) {
			List<String> batchHashes = missing.subList(from, Math.min(missing.size(), from + BATCH));
			List<String> batch = batchHashes.stream().map(byHash::get).toList();
			List<float[]> made = call(target, batch);
			for (int i = 0; i < made.size(); i++) {
				float[] unit = Vectors.normalize(made.get(i));
				vectors.put(batchHashes.get(i), unit);
				store.cache(modelKey, batchHashes.get(i), unit, clock.instant());
			}
		}
		return hashes.stream().map(hash -> hash == null ? new float[0] : vectors.get(hash)).toList();
	}

	private List<float[]> call(AiProviderService.Target target, List<String> batch) {
		AiClient client = clients.get(target.connection().type());
		String provider = target.connection().name();
		long started = System.nanoTime();
		AiEmbeddings reply;
		try {
			reply = resilience.call("ai-" + provider, () -> client.embed(target.connection(), target.model(), batch));
		}
		catch (RuntimeException ex) {
			usage.record(null, PURPOSE, provider, target.model(), 0, 0, 0, (System.nanoTime() - started) / 1_000_000, false);
			throw new EmbeddingUnavailableException(provider + ": " + (ex.getMessage() == null ? ex.getClass().getSimpleName()
					: ex.getMessage()));
		}
		long latency = (System.nanoTime() - started) / 1_000_000;
		usage.record(null, PURPOSE, provider, target.model(), reply.tokens(), 0, usage.cost(target, reply.tokens(), 0),
				latency, true);
		return reply.vectors();
	}

	private static String clip(String text) {
		if (text == null) {
			return "";
		}
		String stripped = text.strip();
		return stripped.length() > MAX_CHARS ? stripped.substring(0, MAX_CHARS) : stripped;
	}

}
