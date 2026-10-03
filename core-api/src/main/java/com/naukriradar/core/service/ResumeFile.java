package com.naukriradar.core.service;

import java.io.InputStream;

/** An open stored resume, ready to stream to the client. The caller closes {@link #content}. */
public record ResumeFile(String fileName, String contentType, long sizeBytes, InputStream content) {
}
