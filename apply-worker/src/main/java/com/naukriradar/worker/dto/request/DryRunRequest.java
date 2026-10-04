package com.naukriradar.worker.dto.request;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.hibernate.validator.constraints.URL;

/** A form to fill without submitting, to check a portal's selectors. */
public record DryRunRequest(
		@NotBlank @URL String url,
		@NotEmpty Map<String, String> selectors,
		Map<String, String> answers) {
}
