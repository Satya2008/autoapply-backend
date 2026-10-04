package com.naukriradar.matching.model;

import java.util.List;

/**
 * The APIs we can speak. Each provider account added by an admin has one of these types; the
 * model is free text, so a new model needs no code change. OPENAI also covers every service
 * with an OpenAI-compatible API (Groq, OpenRouter, DeepSeek, Together, LM Studio...): give
 * its base URL.
 */
public enum AiProviderType {

	ANTHROPIC("Anthropic (Claude)", "https://api.anthropic.com", true,
			List.of("claude-haiku-4-5-20251001", "claude-sonnet-5", "claude-opus-5-5"),
			"Key from console.anthropic.com."),

	OPENAI("OpenAI or any OpenAI-compatible API", "https://api.openai.com", true,
			List.of("gpt-4o-mini", "gpt-4o"),
			"Also Groq (https://api.groq.com/openai), OpenRouter (https://openrouter.ai/api), "
					+ "DeepSeek (https://api.deepseek.com): give the base URL without /v1."),

	GEMINI("Google Gemini", "https://generativelanguage.googleapis.com", true,
			List.of("gemini-2.0-flash", "gemini-2.5-pro"),
			"Key from aistudio.google.com; has a free tier."),

	OLLAMA("Ollama (local, free)", "http://localhost:11434", false,
			List.of("llama3.2", "qwen2.5"),
			"Runs on your machine; no key. Pull a model first: ollama pull llama3.2.");

	private final String label;
	private final String defaultBaseUrl;
	private final boolean needsApiKey;
	private final List<String> exampleModels;
	private final String note;

	AiProviderType(String label, String defaultBaseUrl, boolean needsApiKey, List<String> exampleModels, String note) {
		this.label = label;
		this.defaultBaseUrl = defaultBaseUrl;
		this.needsApiKey = needsApiKey;
		this.exampleModels = exampleModels;
		this.note = note;
	}

	public String label() {
		return label;
	}

	public String defaultBaseUrl() {
		return defaultBaseUrl;
	}

	public boolean needsApiKey() {
		return needsApiKey;
	}

	public List<String> exampleModels() {
		return exampleModels;
	}

	public String note() {
		return note;
	}

}
