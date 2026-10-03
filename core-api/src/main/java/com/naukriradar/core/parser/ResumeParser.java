package com.naukriradar.core.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.naukriradar.core.config.ResumeProperties;
import com.naukriradar.core.exception.InvalidResumeException;
import com.naukriradar.core.model.DocumentType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Detects the type of an uploaded resume and extracts its text with the matching extractor. */
@Component
public class ResumeParser {

	private final DocumentTypeDetector detector;

	private final Map<DocumentType, ResumeTextExtractor> extractors = new EnumMap<>(DocumentType.class);

	private final int maxTextChars;

	public ResumeParser(DocumentTypeDetector detector, List<ResumeTextExtractor> extractors,
			ResumeProperties properties) {
		this.detector = detector;
		this.maxTextChars = properties.maxTextChars();
		for (ResumeTextExtractor extractor : extractors) {
			if (this.extractors.put(extractor.supports(), extractor) != null) {
				throw new IllegalStateException("Two extractors for " + extractor.supports());
			}
		}
	}

	public ParsedResume parse(MultipartFile file) {
		DocumentType type = detector.detect(open(file));
		ResumeTextExtractor extractor = extractors.get(type);
		if (extractor == null) {
			throw new IllegalStateException("No extractor for " + type);
		}
		return new ParsedResume(type, clean(extractor.extract(open(file))));
	}

	/** Drops characters MySQL text columns dislike, tidies whitespace and caps the length. */
	private String clean(String text) {
		if (text == null) {
			return "";
		}
		String cleaned = text.replace("\u0000", "")
				.replace(' ', ' ')
				.replaceAll("[ \\t]+", " ")
				.replaceAll("(\\R\\s*){3,}", "\n\n")
				.strip();
		if (cleaned.length() <= maxTextChars) {
			return cleaned;
		}
		int end = maxTextChars;
		// Don't split a surrogate pair (emoji and the like) in half.
		if (Character.isHighSurrogate(cleaned.charAt(end - 1))) {
			end--;
		}
		return cleaned.substring(0, end);
	}

	private static InputStream open(MultipartFile file) {
		try {
			return file.getInputStream();
		}
		catch (IOException ex) {
			throw new InvalidResumeException("The upload could not be read.", ex);
		}
	}

}
