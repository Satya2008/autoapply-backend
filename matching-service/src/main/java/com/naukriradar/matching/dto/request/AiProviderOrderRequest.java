package com.naukriradar.matching.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/** Provider names in the order to try them; any not listed keep their order after these. */
public record AiProviderOrderRequest(@NotEmpty List<String> names) {
}
