package com.naukriradar.core.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Hard rules for which jobs are matched at all, before any scoring: a job that breaks one is
 * never shown, however well it scores otherwise.
 *
 * @param mustHave the job must be about at least one of these (in its title, its required
 *     skills, or more than once in its description), e.g. "Java"; empty for no rule
 * @param region {@code INDIA}: jobs in India, and remote jobs open to India; {@code ANYWHERE}
 *     (or null): no rule
 * @param level {@code JUNIOR_MID}: no senior, lead or higher roles; {@code UP_TO_SENIOR}: no
 *     staff, principal, lead or higher; {@code ANY} (or null): no rule
 */
public record JobFilters(
		@Size(max = 20) List<@NotBlank @Size(max = 50) String> mustHave,
		@Pattern(regexp = "INDIA|ANYWHERE") String region,
		@Pattern(regexp = "JUNIOR_MID|UP_TO_SENIOR|ANY") String level) {

	public static final JobFilters NONE = new JobFilters(List.of(), "ANYWHERE", "ANY");

}
