package com.naukriradar.core.parser;

import com.naukriradar.core.model.DocumentType;

public record ParsedResume(DocumentType type, String text) {
}
