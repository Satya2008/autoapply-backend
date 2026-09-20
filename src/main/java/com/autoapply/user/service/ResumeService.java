package com.autoapply.user.service;

import com.autoapply.audit.AuditAction;
import com.autoapply.audit.AuditService;
import com.autoapply.common.AppException;
import com.autoapply.settings.SettingKeys;
import com.autoapply.settings.SettingsService;
import com.autoapply.user.entity.User;
import com.autoapply.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Stores an uploaded resume, extracts its text, and derives skills from it using the
 * dictionary configured in settings - so recognising a new technology is a settings edit.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeService {

    private final UserRepository userRepository;
    private final SettingsService settings;
    private final AuditService auditService;

    @Transactional
    public User upload(String email, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw AppException.badRequest("No file was uploaded");
        }

        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("resume");
        String extension = extensionOf(originalName);
        List<String> allowed = settings.getList(SettingKeys.RESUME_ALLOWED_TYPES);
        if (!allowed.isEmpty() && !allowed.contains(extension)) {
            throw AppException.badRequest("Only these file types are allowed: " + String.join(", ", allowed));
        }

        long maxBytes = settings.getLong(SettingKeys.RESUME_MAX_SIZE_MB, 10) * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw AppException.badRequest("File is larger than the " + (maxBytes / 1024 / 1024) + " MB limit");
        }

        User user = userRepository.findByEmail(email).orElseThrow(() -> AppException.notFound("User"));

        try {
            Path directory = Paths.get(settings.getString(SettingKeys.RESUME_UPLOAD_DIR, "./data/resumes"));
            Files.createDirectories(directory);
            String storedName = user.getId() + "_" + System.currentTimeMillis() + "." + extension;
            Path target = directory.resolve(storedName);
            try (var input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }

            String text = extractText(file.getBytes(), extension);

            user.setResumeFilePath(target.toAbsolutePath().toString());
            user.setResumeFileName(originalName);
            user.setResumeText(text);
            user.setResumeUploadedAt(LocalDateTime.now());

            if (settings.getBoolean(SettingKeys.RESUME_AUTO_EXTRACT_SKILLS, true) && !text.isBlank()) {
                List<String> found = extractSkills(text);
                if (!found.isEmpty()) {
                    Set<String> merged = new LinkedHashSet<>(
                            user.getSkills() == null ? List.of() : user.getSkills());
                    merged.addAll(found);
                    user.setSkills(new ArrayList<>(merged));
                    log.info("Extracted {} skills from the resume of {}", found.size(), email);
                }
            }

            User saved = userRepository.save(user);
            auditService.record(email, AuditAction.RESUME_UPLOADED, "user", user.getId(), originalName);
            return saved;

        } catch (IOException e) {
            throw AppException.internal("Could not store the resume: " + e.getMessage());
        }
    }

    public String extractText(byte[] bytes, String extension) {
        try {
            if ("pdf".equals(extension)) {
                try (PDDocument document = Loader.loadPDF(bytes)) {
                    return new PDFTextStripper().getText(document);
                }
            }
            if ("docx".equals(extension)) {
                try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
                     XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                    return extractor.getText();
                }
            }
            return new String(bytes);
        } catch (Exception e) {
            log.warn("Could not extract text from the resume: {}", e.getMessage());
            return "";
        }
    }

    public List<String> extractSkills(String text) {
        String haystack = text.toLowerCase();
        List<String> dictionary = settings.getList(SettingKeys.RESUME_SKILL_DICTIONARY);
        List<String> found = new ArrayList<>();
        for (String skill : dictionary) {
            String needle = skill.toLowerCase().trim();
            if (needle.isEmpty()) continue;
            if (haystack.contains(needle)) {
                found.add(skill.trim());
            }
        }
        return found;
    }

    public byte[] download(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> AppException.notFound("User"));
        if (user.getResumeFilePath() == null) throw AppException.notFound("Resume");
        try {
            return Files.readAllBytes(Paths.get(user.getResumeFilePath()));
        } catch (IOException e) {
            throw AppException.notFound("Resume file");
        }
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }
}
