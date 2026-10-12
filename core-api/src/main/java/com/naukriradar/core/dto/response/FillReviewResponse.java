package com.naukriradar.core.dto.response;

import java.util.List;
import java.util.Map;

/**
 * What "Fill & Review" did: the form is open in Chrome with these filled, the AI answered
 * these, and these are left for the user before they press Submit.
 *
 * @param opened false if the form couldn't be opened at all (then {@code note} says why)
 */
public record FillReviewResponse(boolean opened, List<String> filled, Map<String, String> aiAnswered,
		List<String> leftForYou, String note) {
}
