package com.naukriradar.core.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

/** @param url a real job form on this portal, to try the selectors on */
public record PortalDryRunRequest(@NotBlank @URL String url) {
}
