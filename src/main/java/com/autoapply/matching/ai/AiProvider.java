package com.autoapply.matching.ai;

public interface AiProvider {

    /** Matches the value of the ai.provider setting. */
    String name();

    /** Returns the raw text completion for a prompt, or throws if the call fails. */
    String complete(String prompt);

    /** True when the provider has everything it needs (API key, URL) to be called. */
    boolean isConfigured();
}
