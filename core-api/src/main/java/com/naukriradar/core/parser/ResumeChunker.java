package com.naukriradar.core.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.naukriradar.core.model.ResumeSection;
import org.springframework.stereotype.Component;

/**
 * Cuts resume text into chunks of a few hundred characters, each inside one section
 * (experience, projects...). Small enough that retrieval can pick just the relevant ones,
 * big enough that a chunk still reads as a whole (one job, one project).
 *
 * <p>Headings are recognised by their usual names on a short line of their own; text before
 * the first heading is the header (name, contact details) and is never sent to AI.
 */
@Component
public class ResumeChunker {

	static final int MAX_CHARS = 700;

	static final int MIN_CHARS = 40;

	static final int MAX_CHUNKS = 60;

	private static final Pattern BULLET = Pattern.compile("^[\\u2022\\u25AA\\u25CF\\u2023\\-*\\u2013]\\s+.*");

	private static final Map<ResumeSection, List<String>> HEADINGS = new LinkedHashMap<>();

	static {
		HEADINGS.put(ResumeSection.SUMMARY, List.of("summary", "profile", "profile summary", "professional summary",
				"objective", "career objective", "about me", "about"));
		HEADINGS.put(ResumeSection.EXPERIENCE, List.of("experience", "work experience", "professional experience",
				"employment", "employment history", "work history", "internships", "internship"));
		HEADINGS.put(ResumeSection.PROJECTS, List.of("projects", "personal projects", "key projects", "academic projects",
				"project experience"));
		HEADINGS.put(ResumeSection.SKILLS, List.of("skills", "technical skills", "key skills", "core competencies",
				"technologies", "tech stack", "tools"));
		HEADINGS.put(ResumeSection.EDUCATION, List.of("education", "academics", "academic background", "qualifications",
				"educational qualifications"));
		HEADINGS.put(ResumeSection.ACHIEVEMENTS, List.of("achievements", "awards", "certifications", "certificates",
				"accomplishments", "honors", "honours"));
	}

	public List<Chunk> chunk(String text) {
		List<Chunk> chunks = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return chunks;
		}
		ResumeSection section = ResumeSection.HEADER;
		StringBuilder current = new StringBuilder();
		for (String rawLine : text.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
			String line = rawLine.strip();
			ResumeSection heading = heading(line);
			if (heading != null) {
				flush(chunks, section, current);
				section = heading;
				continue;
			}
			if (line.isEmpty()) {
				// a blank line ends a paragraph: a natural place to cut once the chunk is big enough
				if (current.length() >= MAX_CHARS / 2) {
					flush(chunks, section, current);
				}
				continue;
			}
			boolean newItem = BULLET.matcher(line).matches();
			if (current.length() + line.length() + 1 > MAX_CHARS || (newItem && current.length() >= MAX_CHARS * 2 / 3)) {
				flush(chunks, section, current);
			}
			for (String piece : split(line)) {
				if (current.length() + piece.length() + 1 > MAX_CHARS) {
					flush(chunks, section, current);
				}
				if (!current.isEmpty()) {
					current.append('\n');
				}
				current.append(piece);
			}
		}
		flush(chunks, section, current);
		return chunks.size() > MAX_CHUNKS ? List.copyOf(chunks.subList(0, MAX_CHUNKS)) : chunks;
	}

	/** A short line that is only a known heading, with or without a colon, in any case. */
	static ResumeSection heading(String line) {
		if (line.isEmpty() || line.length() > 40) {
			return null;
		}
		String key = line.replaceAll("[:\\s]+$", "").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
		for (Map.Entry<ResumeSection, List<String>> entry : HEADINGS.entrySet()) {
			if (entry.getValue().contains(key)) {
				return entry.getKey();
			}
		}
		return null;
	}

	/** A line longer than a chunk (PDFs often lose line breaks) is cut at sentence ends, else hard. */
	private static List<String> split(String line) {
		if (line.length() <= MAX_CHARS) {
			return List.of(line);
		}
		List<String> pieces = new ArrayList<>();
		StringBuilder piece = new StringBuilder();
		for (String sentence : line.split("(?<=[.!?;])\\s+")) {
			if (piece.length() + sentence.length() + 1 > MAX_CHARS && !piece.isEmpty()) {
				pieces.add(piece.toString());
				piece.setLength(0);
			}
			while (sentence.length() > MAX_CHARS) {
				pieces.add(sentence.substring(0, MAX_CHARS));
				sentence = sentence.substring(MAX_CHARS);
			}
			if (!piece.isEmpty()) {
				piece.append(' ');
			}
			piece.append(sentence);
		}
		if (!piece.isEmpty()) {
			pieces.add(piece.toString());
		}
		return pieces;
	}

	/** Tiny leftovers join the chunk before them in the same section rather than standing alone. */
	private static void flush(List<Chunk> chunks, ResumeSection section, StringBuilder current) {
		String text = current.toString().strip();
		current.setLength(0);
		if (text.isEmpty()) {
			return;
		}
		if (text.length() < MIN_CHARS && !chunks.isEmpty() && chunks.getLast().section() == section
				&& chunks.getLast().text().length() + text.length() < MAX_CHARS) {
			Chunk last = chunks.removeLast();
			chunks.add(new Chunk(section, last.text() + "\n" + text));
			return;
		}
		chunks.add(new Chunk(section, text));
	}

	public record Chunk(ResumeSection section, String text) {
	}

}
