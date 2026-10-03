package com.naukriradar.core.mapper;

import com.naukriradar.core.dto.response.ResumeResponse;
import com.naukriradar.core.model.Resume;
import org.springframework.stereotype.Component;

@Component
public class ResumeMapper {

	public ResumeResponse toResponse(Resume resume) {
		return new ResumeResponse(
				resume.getId(),
				resume.getFileName(),
				resume.getDocumentType().getContentType(),
				resume.getSizeBytes(),
				resume.getUploadedAt(),
				resume.hasText());
	}

}
