package com.naukriradar.core.audit;

import com.naukriradar.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Who is acting and from where, read on the request thread. Outside a request (scheduled
 * jobs) the actor is "system". Until Phase 8 the actor is whatever X-User-Id says, or
 * "anonymous" when it's missing.
 */
public record RequestOrigin(String actor, String ip, String userAgent) {

	static final RequestOrigin SYSTEM = new RequestOrigin("system", null, null);

	public static RequestOrigin current() {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (!(attributes instanceof ServletRequestAttributes servlet)) {
			return SYSTEM;
		}
		HttpServletRequest request = servlet.getRequest();
		String actor = request.getHeader(CurrentUserProvider.USER_ID_HEADER);
		return new RequestOrigin(actor == null || actor.isBlank() ? "anonymous" : actor.strip(), clientIp(request),
				request.getHeader("User-Agent"));
	}

	/** The first address in X-Forwarded-For (set by the gateway), else the socket's peer. */
	private static String clientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].strip();
		}
		return request.getRemoteAddr();
	}

}
