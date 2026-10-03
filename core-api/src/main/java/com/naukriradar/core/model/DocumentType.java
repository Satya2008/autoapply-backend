package com.naukriradar.core.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Resume formats we accept. Detected from the file's bytes, never from its name. */
@Getter
@RequiredArgsConstructor
public enum DocumentType {

	PDF("application/pdf", ".pdf"),
	DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx");

	private final String contentType;

	private final String extension;

}
