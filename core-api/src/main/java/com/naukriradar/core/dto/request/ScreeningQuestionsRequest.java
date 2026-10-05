package com.naukriradar.core.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** The questions an application form asks, e.g. "How many years of Java experience do you have?". */
public record ScreeningQuestionsRequest(
		@NotEmpty @Size(max = 10) List<@NotBlank @Size(max = 300) String> questions) {
}
