package com.naukriradar.core.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The new value as text: "50", "true", "45m", "0 0 9 * * *", "a.com, b.com". */
public record SettingUpdateRequest(@NotNull @Size(max = 4000) String value) {
}
