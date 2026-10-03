package com.naukriradar.core.parser;

import java.io.IOException;
import java.io.InputStream;

import com.naukriradar.core.exception.InvalidResumeException;
import com.naukriradar.core.model.DocumentType;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

@Component
public class DocxTextExtractor implements ResumeTextExtractor {

	@Override
	public DocumentType supports() {
		return DocumentType.DOCX;
	}

	@Override
	public String extract(InputStream content) {
		// POI's zip-bomb guard is on by default, so a crafted archive fails here.
		try (InputStream in = content;
				XWPFDocument document = new XWPFDocument(in);
				XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
			return extractor.getText();
		}
		catch (IOException | RuntimeException ex) {
			// Covers plain zips that aren't Word files, broken archives and zip bombs.
			throw new InvalidResumeException("The file is not a valid Word (.docx) document.", ex);
		}
	}

}
