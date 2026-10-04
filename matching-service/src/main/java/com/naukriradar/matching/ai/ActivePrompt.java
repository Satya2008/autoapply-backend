package com.naukriradar.matching.ai;

import java.util.Map;

/** The active version of a prompt, ready to fill in. */
public record ActivePrompt(String code, int version, String system, String template, Map<String, Object> outputSchema) {
}
