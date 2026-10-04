package com.naukriradar.core.mapper;

import com.naukriradar.core.dto.response.ResumeResponse;
import com.naukriradar.core.model.Resume;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ResumeMapper {

	private final JsonMapper json;

	public ResumeMapper(JsonMapper json) {
		this.json = json;
	}

	public ResumeResponse toResponse(Resume resume) {
		return new ResumeResponse(
				resume.getId(),
				resume.getFileName(),
				resume.getDocumentType().getContentType(),
				resume.getSizeBytes(),
				resume.getUploadedAt(),
				resume.hasText(),
				parsed(resume.getParsedJson()),
				resume.getParsedAt());
	}

	private JsonNode parsed(String parsedJson) {
		if (parsedJson == null) {
			return null;
		}
		try {
			return json.readTree(parsedJson);
		}
		catch (RuntimeException ex) {
			return null;
		}
	}

}
