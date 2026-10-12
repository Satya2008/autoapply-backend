package com.naukriradar.core.dto.response;

/** Shown once to the admin to pass on; the user must change it at their next sign-in. */
public record TemporaryPasswordResponse(String temporaryPassword) {
}
