package com.naukriradar.core.parser;

import java.io.IOException;
import java.io.InputStream;

import com.naukriradar.core.config.ResumeProperties;
import com.naukriradar.core.exception.InvalidResumeException;
import com.naukriradar.core.model.DocumentType;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PdfTextExtractor implements ResumeTextExtractor {

	private final ResumeProperties properties;

	@Override
	public DocumentType supports() {
		return DocumentType.PDF;
	}

	@Override
	public String extract(InputStream content) {
		// Temp-file cache keeps big PDFs off the heap; services run with a small one.
		try (InputStream in = content;
				PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(in), "", null, null,
						IOUtils.createTempFileOnlyStreamCache())) {
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(true);
			stripper.setEndPage(Math.min(document.getNumberOfPages(), properties.maxPages()));
			return stripper.getText(document);
		}
		catch (InvalidPasswordException ex) {
			throw new InvalidResumeException("The PDF is password-protected. Upload a copy without a password.", ex);
		}
		catch (IOException | RuntimeException ex) {
			// PDFBox can throw almost anything on a damaged file.
			throw new InvalidResumeException("The PDF could not be read. It may be damaged.", ex);
		}
	}

}
