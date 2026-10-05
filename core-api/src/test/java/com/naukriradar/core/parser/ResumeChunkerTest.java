package com.naukriradar.core.parser;

import java.util.List;

import com.naukriradar.core.model.ResumeSection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeChunkerTest {

	private final ResumeChunker chunker = new ResumeChunker();

	@Test
	void eachChunkStaysInsideItsSectionAndTheHeaderIsKeptApart() {
		List<ResumeChunker.Chunk> chunks = chunker.chunk("""
				Asha Rao
				asha@example.com | +91 98765 43210

				WORK EXPERIENCE:
				Backend developer at Acme Corp, 2021-2024
				- Built payment services in Java and Kafka
				Projects
				Order tracker in Spring Boot with MySQL
				Education
				B.Tech, 2019""");

		assertThat(chunks).extracting(ResumeChunker.Chunk::section).containsExactly(ResumeSection.HEADER,
				ResumeSection.EXPERIENCE, ResumeSection.PROJECTS, ResumeSection.EDUCATION);
		assertThat(chunks.get(1).text()).contains("Acme Corp").contains("Kafka").doesNotContain("asha@");
	}

	@Test
	void longSectionsAreCutIntoReadableChunksAndLongLinesAtSentences() {
		String sentence = "Designed and ran a high-volume service that handled payments for many merchants. ";
		String text = "Experience\n" + sentence.repeat(30) + "\n" + "x".repeat(2000);

		List<ResumeChunker.Chunk> chunks = chunker.chunk(text);

		assertThat(chunks).hasSizeGreaterThan(3).allSatisfy(c -> {
			assertThat(c.section()).isEqualTo(ResumeSection.EXPERIENCE);
			assertThat(c.text().length()).isLessThanOrEqualTo(ResumeChunker.MAX_CHARS);
		});
		assertThat(chunks.getFirst().text()).endsWith("merchants.");
	}

	@Test
	void headingsAreRecognisedOnlyOnAShortLineOfTheirOwn() {
		assertThat(ResumeChunker.heading("Technical Skills:")).isEqualTo(ResumeSection.SKILLS);
		assertThat(ResumeChunker.heading("  PROFESSIONAL   EXPERIENCE ")).isNull();
		assertThat(ResumeChunker.heading("PROFESSIONAL EXPERIENCE")).isEqualTo(ResumeSection.EXPERIENCE);
		assertThat(ResumeChunker.heading("Experience with Kafka and Spark in production")).isNull();
		assertThat(ResumeChunker.heading("")).isNull();
	}

	@Test
	void emptyTextGivesNothingAndTinyLeftoversJoinTheirNeighbour() {
		assertThat(chunker.chunk(null)).isEmpty();
		assertThat(chunker.chunk("  \n ")).isEmpty();
		String text = "Projects\n" + "A".repeat(400) + "\n\n" + "B".repeat(400) + "\n\nshort";
		List<ResumeChunker.Chunk> chunks = chunker.chunk(text);
		assertThat(chunks).hasSize(2);
		assertThat(chunks.getLast().text()).endsWith("short");
	}

}
