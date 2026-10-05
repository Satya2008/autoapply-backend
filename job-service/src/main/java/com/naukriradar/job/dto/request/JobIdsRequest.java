package com.naukriradar.job.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Jobs asked for by id, e.g. the ones matching found by meaning. */
public record JobIdsRequest(@NotNull @Size(max = 500) List<@NotNull @Size(max = 36) String> ids) {
}
