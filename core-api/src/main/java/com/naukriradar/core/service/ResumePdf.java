package com.naukriradar.core.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.naukriradar.core.dto.response.TailoredResumeResponse.Job;
import com.naukriradar.core.dto.response.TailoredResumeResponse.Project;
import com.naukriradar.core.dto.response.TailoredResumeResponse.Resume;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * A one-column, text-only resume PDF: what applicant tracking systems read best (no tables,
 * columns, icons or images). Built with the PDF's standard fonts, so characters outside them
 * become their nearest plain equivalent.
 */
final class ResumePdf {

	private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
	private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

	private static final float MARGIN = 50;
	private static final float WIDTH = PDRectangle.A4.getWidth() - 2 * MARGIN;

	/** Typography the standard fonts lack, as plain text. */
	private static final Map<Character, String> PLAIN = Map.of('–', "-", '—', "-", '‘', "'", '’', "'",
			'“', "\"", '”', "\"", '₹', "Rs.", '•', "-", ' ', " ", '…', "...");

	private final PDDocument document = new PDDocument();
	private PDPageContentStream out;
	private float y;

	private ResumePdf() {
	}

	/** @param contact one line: email, phone, city, links */
	static byte[] render(String name, String contact, Resume resume) {
		ResumePdf pdf = new ResumePdf();
		try {
			pdf.newPage();
			pdf.line(name == null ? "" : name, BOLD, 18, 22);
			if (contact != null && !contact.isBlank()) {
				pdf.wrapped(contact, REGULAR, 9.5f, 13, 0);
			}
			pdf.section("Summary");
			pdf.wrapped(resume.summary(), REGULAR, 10.5f, 14, 0);
			if (!resume.skills().isEmpty()) {
				pdf.section("Skills");
				pdf.wrapped(String.join(", ", resume.skills()), REGULAR, 10.5f, 14, 0);
			}
			if (!resume.experience().isEmpty()) {
				pdf.section("Experience");
				for (Job job : resume.experience()) {
					pdf.space(4);
					String dates = job.dates() == null || job.dates().isBlank() ? "" : "  |  " + job.dates();
					pdf.wrapped(job.title() + " - " + job.company() + dates, BOLD, 10.5f, 14, 0);
					job.bullets().forEach(b -> pdf.bullet(b));
				}
			}
			if (!resume.projects().isEmpty()) {
				pdf.section("Projects");
				for (Project project : resume.projects()) {
					pdf.space(4);
					pdf.wrapped(project.name(), BOLD, 10.5f, 14, 0);
					project.bullets().forEach(b -> pdf.bullet(b));
				}
			}
			if (!resume.education().isEmpty()) {
				pdf.section("Education");
				resume.education().forEach(e -> pdf.wrapped(e, REGULAR, 10.5f, 14, 0));
			}
			pdf.out.close();
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			pdf.document.save(bytes);
			pdf.document.close();
			return bytes.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not build the resume PDF", ex);
		}
	}

	private void section(String title) throws IOException {
		space(10);
		line(title.toUpperCase(), BOLD, 11.5f, 15);
		ensure(4);
		out.setLineWidth(0.6f);
		out.moveTo(MARGIN, y + 11);
		out.lineTo(MARGIN + WIDTH, y + 11);
		out.stroke();
	}

	private void bullet(String text) {
		wrapped("-  " + text, REGULAR, 10.5f, 14, 12);
	}

	private void wrapped(String text, PDType1Font font, float size, float leading, float indent) {
		try {
			for (String piece : wrap(plain(text), font, size, WIDTH - indent)) {
				ensure(leading);
				write(piece, font, size, MARGIN + indent);
				y -= leading;
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private void line(String text, PDType1Font font, float size, float leading) throws IOException {
		ensure(leading);
		write(plain(text), font, size, MARGIN);
		y -= leading;
	}

	private void write(String text, PDType1Font font, float size, float x) throws IOException {
		out.beginText();
		out.setFont(font, size);
		out.newLineAtOffset(x, y);
		out.showText(text);
		out.endText();
	}

	private void space(float points) {
		y -= points;
	}

	private void ensure(float needed) throws IOException {
		if (y - needed < MARGIN) {
			out.close();
			newPage();
		}
	}

	private void newPage() throws IOException {
		PDPage page = new PDPage(PDRectangle.A4);
		document.addPage(page);
		out = new PDPageContentStream(document, page);
		y = PDRectangle.A4.getHeight() - MARGIN;
	}

	static List<String> wrap(String text, PDType1Font font, float size, float width) throws IOException {
		List<String> lines = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (String word : text.split(" ")) {
			String trial = current.isEmpty() ? word : current + " " + word;
			if (font.getStringWidth(trial) / 1000 * size > width && !current.isEmpty()) {
				lines.add(current.toString());
				current = new StringBuilder(word);
			}
			else {
				current = new StringBuilder(trial);
			}
		}
		if (!current.isEmpty()) {
			lines.add(current.toString());
		}
		return lines;
	}

	/** What the standard fonts can draw: common typography mapped, anything else dropped. */
	static String plain(String text) {
		if (text == null) {
			return "";
		}
		StringBuilder out = new StringBuilder(text.length());
		for (char c : text.replace('\n', ' ').replace('\t', ' ').toCharArray()) {
			String mapped = PLAIN.get(c);
			if (mapped != null) {
				out.append(mapped);
			}
			else if (c >= 32 && c < 127 || c >= 160 && c <= 255) {
				out.append(c);
			}
		}
		return out.toString().replaceAll(" {2,}", " ").strip();
	}

}
