package com.naukriradar.core.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import com.naukriradar.common.exception.BadRequestException;
import com.naukriradar.core.audit.RequestOrigin;
import com.naukriradar.core.dto.response.AuditEntryResponse;
import com.naukriradar.core.dto.response.AuditPageResponse;
import com.naukriradar.core.model.AuditEntry;
import com.naukriradar.core.repository.AuditEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores audit entries off the request thread, so a slow audit table never slows an admin
 * action down. If the small queue is full the entry is logged and dropped rather than
 * blocking the request.
 */
@Service
public class AuditService {

	private static final Logger log = LoggerFactory.getLogger(AuditService.class);

	private final AuditEntryRepository repository;
	private final ThreadPoolTaskExecutor auditPool;
	private final Clock clock = Clock.systemUTC();

	public AuditService(AuditEntryRepository repository, @Qualifier("auditPool") ThreadPoolTaskExecutor auditPool) {
		this.repository = repository;
		this.auditPool = auditPool;
	}

	public void record(RequestOrigin origin, String action, String targetType, String targetId, String detail,
			boolean success, String error) {
		AuditEntry entry = new AuditEntry(origin.actor(), action, targetType, targetId, detail, origin.ip(),
				origin.userAgent(), success, error, clock.instant());
		try {
			auditPool.execute(() -> save(entry));
		}
		catch (RejectedExecutionException ex) {
			log.warn("Audit queue full, dropped {} by {} on {}", action, origin.actor(), targetId);
		}
	}

	@Transactional(readOnly = true)
	public AuditPageResponse search(String actor, String action, String targetType, String cursor, int limit) {
		String after = null;
		if (cursor != null && !cursor.isBlank()) {
			try {
				after = UUID.fromString(cursor.strip()).toString();
			}
			catch (IllegalArgumentException ex) {
				throw new BadRequestException("Invalid cursor. Use the nextCursor value from the previous page.");
			}
		}
		List<AuditEntry> rows = repository.search(blankToNull(actor), blankToNull(action), blankToNull(targetType), after,
				PageRequest.of(0, limit + 1));
		boolean more = rows.size() > limit;
		List<AuditEntry> items = more ? rows.subList(0, limit) : rows;
		return new AuditPageResponse(items.stream()
				.map(e -> new AuditEntryResponse(e.getId(), e.getActor(), e.getAction(), e.getTargetType(), e.getTargetId(),
						e.getDetail(), e.getIp(), e.isSuccess(), e.getError(), e.getAt()))
				.toList(), more ? items.get(items.size() - 1).getId() : null);
	}

	private void save(AuditEntry entry) {
		try {
			repository.save(entry);
		}
		catch (RuntimeException ex) {
			log.error("Could not write audit entry {} by {}", entry.getAction(), entry.getActor(), ex);
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
