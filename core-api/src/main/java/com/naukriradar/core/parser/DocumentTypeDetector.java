package com.naukriradar.core.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.naukriradar.core.exception.InvalidResumeException;
import com.naukriradar.core.model.DocumentType;
import org.springframework.stereotype.Component;

/**
 * Works out the real file type from its first bytes ("magic numbers"). The file name and
 * the Content-Type header come from the client, so neither is trusted.
 */
@Component
public class DocumentTypeDetector {

	/** PDF readers accept the header anywhere in the first 1 KB, so we do too. */
	private static final int PDF_SEARCH_WINDOW = 1024;

	private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

	private static final byte[] ZIP = { 'P', 'K', 3, 4 };

	/** OLE2 container: legacy .doc, and also password-protected .docx. */
	private static final byte[] OLE2 = { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A,
			(byte) 0xE1 };

	private static final byte[] WINDOWS_EXECUTABLE = { 'M', 'Z' };

	private static final byte[] ELF_EXECUTABLE = { 0x7F, 'E', 'L', 'F' };

	public DocumentType detect(InputStream content) {
		byte[] head = readHead(content);
		if (head.length == 0) {
			throw new InvalidResumeException("The file is empty.");
		}
		// Executables first: a file can start like an .exe and still carry "%PDF-" later on.
		if (startsWith(head, WINDOWS_EXECUTABLE) || startsWith(head, ELF_EXECUTABLE)) {
			throw new InvalidResumeException("Executable files are not allowed.");
		}
		if (startsWith(head, OLE2)) {
			throw new InvalidResumeException(
					"Old .doc files and password-protected Word files can't be read. Save it as .docx or PDF.");
		}
		if (startsWith(head, ZIP)) {
			// Every .docx is a zip, but not every zip is a .docx. The DOCX parser confirms it.
			return DocumentType.DOCX;
		}
		if (indexOf(head, PDF) >= 0) {
			return DocumentType.PDF;
		}
		throw new InvalidResumeException("Only PDF and DOCX resumes are supported.");
	}

	private static byte[] readHead(InputStream content) {
		try (content) {
			return content.readNBytes(PDF_SEARCH_WINDOW);
		}
		catch (IOException ex) {
			throw new InvalidResumeException("The file could not be read.", ex);
		}
	}

	private static boolean startsWith(byte[] data, byte[] prefix) {
		return data.length >= prefix.length && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
	}

	private static int indexOf(byte[] data, byte[] needle) {
		outer:
		for (int i = 0; i <= data.length - needle.length; i++) {
			for (int j = 0; j < needle.length; j++) {
				if (data[i + j] != needle[j]) {
					continue outer;
				}
			}
			return i;
		}
		return -1;
	}

}
