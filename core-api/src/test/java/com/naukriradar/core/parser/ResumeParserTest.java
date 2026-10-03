package com.naukriradar.core.parser;

import java.util.List;

import com.naukriradar.core.config.ResumeProperties;
import com.naukriradar.core.exception.InvalidResumeException;
import com.naukriradar.core.model.DocumentType;
import com.naukriradar.core.support.TestDocuments;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumeParserTest {

	private final ResumeParser parser = parser(200_000);

	@Test
	void readsTextFromPdf() {
		ParsedResume parsed = parser.parse(file(TestDocuments.pdf("Satya", "Java Developer", "Spring Boot, MySQL")));

		assertThat(parsed.type()).isEqualTo(DocumentType.PDF);
		assertThat(parsed.text()).contains("Java Developer").contains("Spring Boot, MySQL");
	}

	@Test
	void readsTextFromDocx() {
		ParsedResume parsed = parser.parse(file(TestDocuments.docx("Satya", "Kafka and Redis")));

		assertThat(parsed.type()).isEqualTo(DocumentType.DOCX);
		assertThat(parsed.text()).contains("Kafka and Redis");
	}

	@Test
	void trustsTheBytesNotTheFileName() {
		MockMultipartFile pdfNamedDocx = new MockMultipartFile("file", "cv.docx",
				"application/vnd.openxmlformats-officedocument.wordprocessingml.document", TestDocuments.pdf("Java"));

		assertThat(parser.parse(pdfNamedDocx).type()).isEqualTo(DocumentType.PDF);
	}

	@Test
	void rejectsPasswordProtectedPdf() {
		assertThatThrownBy(() -> parser.parse(file(TestDocuments.passwordProtectedPdf("Java"))))
				.isInstanceOf(InvalidResumeException.class)
				.hasMessageContaining("password");
	}

	@Test
	void rejectsADamagedPdf() {
		byte[] broken = "%PDF-1.7\n this is not really a pdf".getBytes();

		assertThatThrownBy(() -> parser.parse(file(broken)))
				.isInstanceOf(InvalidResumeException.class)
				.hasMessageContaining("could not be read");
	}

	@Test
	void rejectsAZipThatIsNotAWordDocument() {
		assertThatThrownBy(() -> parser.parse(file(TestDocuments.plainZip())))
				.isInstanceOf(InvalidResumeException.class)
				.hasMessageContaining("not a valid Word");
	}

	@Test
	void capsVeryLongText() {
		ResumeParser smallCap = parser(10);

		assertThat(smallCap.parse(file(TestDocuments.docx("abcdefghijklmnopqrstuvwxyz"))).text()).hasSize(10);
	}

	@Test
	void tidiesWhitespace() {
		ParsedResume parsed = parser.parse(file(TestDocuments.docx("Java     Developer", "", "", "", "", "Kafka")));

		assertThat(parsed.text()).isEqualTo("Java Developer\n\nKafka");
	}

	private static MockMultipartFile file(byte[] bytes) {
		return new MockMultipartFile("file", "resume", "application/octet-stream", bytes);
	}

	private static ResumeParser parser(int maxTextChars) {
		ResumeProperties properties = new ResumeProperties(DataSize.ofMegabytes(10), 20, maxTextChars,
				new ClassPathResource("skills/dictionary.txt"));
		return new ResumeParser(new DocumentTypeDetector(),
				List.of(new PdfTextExtractor(properties), new DocxTextExtractor()), properties);
	}

}
