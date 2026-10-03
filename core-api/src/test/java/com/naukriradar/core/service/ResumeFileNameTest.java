package com.naukriradar.core.service;

import com.naukriradar.core.model.DocumentType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeFileNameTest {

	@ParameterizedTest(name = "{0} -> {2}")
	@CsvSource(delimiter = '|', nullValues = "NULL", textBlock = """
			Satya CV.pdf               | PDF  | Satya CV.pdf
			Satya CV.PDF               | PDF  | Satya CV.pdf
			cv                         | PDF  | cv.pdf
			cv.docx                    | PDF  | cv.pdf
			cv.pdf                     | DOCX | cv.docx
			C:\\Users\\me\\cv.pdf      | PDF  | cv.pdf
			../../etc/passwd           | PDF  | passwd.pdf
			"bad"<name>?.pdf           | PDF  | badname.pdf
			NULL                       | PDF  | resume.pdf
			'   '                      | DOCX | resume.docx
			...                        | PDF  | resume.pdf
			""")
	void cleansFileNames(String original, DocumentType type, String expected) {
		assertThat(ResumeService.safeFileName(original, type)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({ "PDF", "DOCX" })
	void capsTheLength(DocumentType type) {
		String name = ResumeService.safeFileName("a".repeat(500) + ".pdf", type);

		assertThat(name).hasSizeLessThanOrEqualTo(200).endsWith(type.getExtension());
	}

}
