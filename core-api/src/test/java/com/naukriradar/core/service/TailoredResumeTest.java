package com.naukriradar.core.service;

import java.util.List;

import com.naukriradar.core.dto.response.TailoredResumeResponse.Job;
import com.naukriradar.core.dto.response.TailoredResumeResponse.Project;
import com.naukriradar.core.dto.response.TailoredResumeResponse.Resume;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class TailoredResumeTest {

	private final Resume resume = new Resume("Java backend developer with 3 years of Spring Boot and Kafka.",
			List.of("Java", "Spring Boot", "Kafka"),
			List.of(new Job("Software Engineer", "Hyperface", "2024 – Present",
					List.of("Built REST APIs with Spring Boot.", "Ran Kafka consumers with retries."))),
			List.of(new Project("NaukriRadar", List.of("Job matching with embeddings."))),
			List.of("M.A., Sociology – MGKVP, 2017"));

	@Test
	void readsTheAisAnswer() {
		JsonMapper json = JsonMapper.builder().build();
		Resume read = TailoredResumeService.parse(json.readTree("""
				{"summary": "Fit.", "skills": ["Java", " "], "experience": [{"title": "SE", "company": "X", "bullets": ["a"]}],
				 "projects": [], "education": ["B.A."]}"""));

		assertThat(read.skills()).containsExactly("Java");
		assertThat(read.experience()).singleElement().satisfies(j -> {
			assertThat(j.dates()).isEmpty();
			assertThat(j.bullets()).containsExactly("a");
		});
	}

	@Test
	void theWholeResumeIsCheckedAsText() {
		assertThat(TailoredResumeService.text(resume)).contains("Hyperface", "Ran Kafka consumers", "NaukriRadar", "M.A.");
	}

	@Test
	void thePdfIsPlainTextAnAtsCanRead() throws Exception {
		byte[] pdf = ResumePdf.render("Satyanand Shrivastava", "satya@example.com  |  +91 96166 92093", resume);

		try (PDDocument document = Loader.loadPDF(pdf)) {
			String text = new PDFTextStripper().getText(document);
			assertThat(text).contains("Satyanand Shrivastava", "SUMMARY", "SKILLS", "Java, Spring Boot, Kafka", "EXPERIENCE",
					"Software Engineer - Hyperface", "2024 - Present", "Built REST APIs", "PROJECTS", "EDUCATION");
		}
	}

	@Test
	void typographyTheStandardFontsLackBecomesPlain() {
		assertThat(ResumePdf.plain("2024 – Present • ₹7 lakh “quoted” 😀")).isEqualTo("2024 - Present - Rs.7 lakh \"quoted\"");
	}

}
