package com.naukriradar.matching.ai;

/** What a provider sent back, before any checking. */
public record AiCompletion(String text, long tokensIn, long tokensOut) {
}
