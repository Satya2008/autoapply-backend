package com.naukriradar.core.parser;

import java.io.InputStream;

import com.naukriradar.core.model.DocumentType;

/** Pulls plain text out of one document format. Add a format by adding an implementation. */
public interface ResumeTextExtractor {

	DocumentType supports();

	/**
	 * @throws com.naukriradar.core.exception.InvalidResumeException if the content is not a
	 * readable document of this type
	 */
	String extract(InputStream content);

}
