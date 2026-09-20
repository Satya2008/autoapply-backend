package com.autoapply.audit;

import com.autoapply.settings.SettingKeys;
import com.autoapply.settings.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository repository;
    private final SettingsService settings;

    @Async
    public void record(String actor, AuditAction action, String targetType, String targetId, String detail) {
        write(actor, action, targetType, targetId, detail, true);
    }

    @Async
    public void recordFailure(String actor, AuditAction action, String targetType, String targetId, String detail) {
        write(actor, action, targetType, targetId, detail, false);
    }

    private void write(String actor, AuditAction action, String targetType, String targetId,
                       String detail, boolean success) {
        if (!settings.getBoolean(SettingKeys.AUDIT_ENABLED, true)) return;
        try {
            AuditLog entry = AuditLog.builder()
                    .actor(actor == null ? "system" : actor)
                    .action(action)
                    .targetType(targetType)
                    .targetId(truncate(targetId, 250))
                    .detail(truncate(detail, 4000))
                    .success(success)
                    .build();
            enrichFromRequest(entry);
            repository.save(entry);
        } catch (Exception e) {
            log.debug("Could not write audit entry: {}", e.getMessage());
        }
    }

    private void enrichFromRequest(AuditLog entry) {
        try {
            var attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                HttpServletRequest request = servletAttributes.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                entry.setIpAddress(forwarded != null && !forwarded.isBlank()
                        ? forwarded.split(",")[0].trim()
                        : request.getRemoteAddr());
                entry.setUserAgent(truncate(request.getHeader("User-Agent"), 390));
            }
        } catch (Exception ignored) {
            // audit enrichment is best-effort
        }
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    public Page<AuditLog> search(String actor, AuditAction action, Pageable pageable) {
        if (actor != null && !actor.isBlank()) {
            return repository.findByActorOrderByCreatedAtDesc(actor, pageable);
        }
        if (action != null) {
            return repository.findByActionOrderByCreatedAtDesc(action, pageable);
        }
        return repository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional
    public int purgeOlderThan(int days) {
        int removed = repository.deleteOlderThan(LocalDateTime.now().minusDays(days));
        if (removed > 0) log.info("Purged {} audit entries older than {} days", removed, days);
        return removed;
    }
}
