package com.naukriradar.core.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/** Builds real PDF and DOCX files in memory, so tests don't depend on files checked in. */
public final class TestDocuments {

	private TestDocuments() {
	}

	public static byte[] pdf(String... lines) {
		return pdf(null, lines);
	}

	public static byte[] passwordProtectedPdf(String... lines) {
		return pdf("secret", lines);
	}

	public static byte[] docx(String... paragraphs) {
		try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			for (String paragraph : paragraphs) {
				document.createParagraph().createRun().setText(paragraph);
			}
			document.write(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** A valid zip that is not a Word document. */
	public static byte[] plainZip() {
		try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("notes.txt"));
			zip.write("hello".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
			zip.finish();
			return bytes.toByteArray();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Starts like a Windows .exe. */
	public static byte[] executable() {
		byte[] bytes = new byte[256];
		bytes[0] = 'M';
		bytes[1] = 'Z';
		return bytes;
	}

	private static byte[] pdf(String userPassword, String... lines) {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				content.setLeading(16);
				content.newLineAtOffset(50, 720);
				for (String line : lines) {
					content.showText(line);
					content.newLine();
				}
				content.endText();
			}
			if (userPassword != null) {
				StandardProtectionPolicy policy = new StandardProtectionPolicy("owner", userPassword, new AccessPermission());
				policy.setEncryptionKeyLength(128);
				document.protect(policy);
			}
			document.save(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
