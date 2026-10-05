package com.naukriradar.matching.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Which of these passages answer the query best.
 *
 * @param top how many to return; default 3
 */
public record RetrieveRequest(
		@NotBlank @Size(max = 4000) String query,
		@NotNull @Size(max = 200) List<@Valid @NotNull Passage> passages,
		@Min(1) @Max(20) Integer top) {

	public int topOrDefault() {
		return top == null ? 3 : top;
	}

	/** @param id the caller's own id for the passage, given back as is */
	public record Passage(@NotBlank @Size(max = 64) String id, @NotNull @Size(max = 8000) String text) {
	}

}
