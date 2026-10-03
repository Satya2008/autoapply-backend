package com.naukriradar.core.parser;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import com.naukriradar.core.exception.InvalidResumeException;
import com.naukriradar.core.model.DocumentType;
import com.naukriradar.core.support.TestDocuments;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTypeDetectorTest {

	private final DocumentTypeDetector detector = new DocumentTypeDetector();

	@Test
	void detectsPdf() {
		assertThat(detect(TestDocuments.pdf("hello"))).isEqualTo(DocumentType.PDF);
	}

	@Test
	void detectsPdfWithJunkBeforeTheHeader() {
		byte[] withJunk = ("\r\n\r\n" + "%PDF-1.7 rest").getBytes(StandardCharsets.US_ASCII);
		assertThat(detect(withJunk)).isEqualTo(DocumentType.PDF);
	}

	@Test
	void detectsDocx() {
		assertThat(detect(TestDocuments.docx("hello"))).isEqualTo(DocumentType.DOCX);
	}

	@Test
	void rejectsAnExecutableEvenIfItAlsoContainsAPdfHeader() {
		byte[] polyglot = TestDocuments.executable();
		System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, polyglot, 100, 5);

		assertThatThrownBy(() -> detect(polyglot))
				.isInstanceOf(InvalidResumeException.class)
				.hasMessageContaining("Executable");
	}

	@Test
	void rejectsLegacyWordFilesWithAHelpfulMessage() {
		byte[] doc = { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 0 };

		assertThatThrownBy(() -> detect(doc)).hasMessageContaining(".docx or PDF");
	}

	@Test
	void rejectsPlainTextAndImages() {
		assertThatThrownBy(() -> detect("just text".getBytes(StandardCharsets.UTF_8)))
				.hasMessageContaining("Only PDF and DOCX");
		byte[] png = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
		assertThatThrownBy(() -> detect(png)).hasMessageContaining("Only PDF and DOCX");
	}

	@Test
	void rejectsEmptyContent() {
		assertThatThrownBy(() -> detect(new byte[0])).hasMessageContaining("empty");
	}

	private DocumentType detect(byte[] bytes) {
		return detector.detect(new ByteArrayInputStream(bytes));
	}

}
