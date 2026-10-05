package com.naukriradar.matching.dto.request;

import com.naukriradar.matching.model.EvalKind;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param promptCode for PROMPT: which prompt
 * @param promptVersion for PROMPT: which version; the newest when left out
 */
public record EvalRunRequest(@NotNull EvalKind kind, @Size(max = 50) String promptCode, @Min(1) Integer promptVersion) {
}
