package com.naukriradar.core.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.springframework.web.multipart.MultipartFile;

/**
 * A file the browser uploaded straight to object storage, read back so it can go through the
 * same checks and parsing as a normal upload.
 */
class StoredUpload implements MultipartFile {

	private final String fileName;
	private final String contentType;
	private final byte[] bytes;

	StoredUpload(String fileName, String contentType, byte[] bytes) {
		this.fileName = fileName;
		this.contentType = contentType;
		this.bytes = bytes;
	}

	@Override
	public String getName() {
		return "file";
	}

	@Override
	public String getOriginalFilename() {
		return fileName;
	}

	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	public boolean isEmpty() {
		return bytes.length == 0;
	}

	@Override
	public long getSize() {
		return bytes.length;
	}

	@Override
	public byte[] getBytes() {
		return bytes.clone();
	}

	@Override
	public InputStream getInputStream() {
		return new ByteArrayInputStream(bytes);
	}

	@Override
	public void transferTo(File dest) throws IOException {
		Files.write(dest.toPath(), bytes);
	}

}
