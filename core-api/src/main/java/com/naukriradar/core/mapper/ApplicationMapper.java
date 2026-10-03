package com.naukriradar.core.mapper;

import java.util.List;
import java.util.Map;

import com.naukriradar.core.dto.response.ApplicationDetailResponse;
import com.naukriradar.core.dto.response.ApplicationEventResponse;
import com.naukriradar.core.dto.response.ApplicationSummaryResponse;
import com.naukriradar.core.dto.response.ApplyRunResponse;
import com.naukriradar.core.dto.response.NeedsYouResponse;
import com.naukriradar.core.model.Application;
import com.naukriradar.core.model.ApplicationEvent;
import com.naukriradar.core.model.ApplyRun;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ApplicationMapper {

	private static final TypeReference<Map<String, String>> PREFILL = new TypeReference<>() {
	};

	private final JsonMapper jsonMapper;

	public ApplicationMapper(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public ApplyRunResponse toResponse(ApplyRun run) {
		return new ApplyRunResponse(run.getId(), run.getStatus(), run.getMatchesConsidered(), run.getQueued(),
				run.getNeedsYou(), run.getSimulated(), run.getFailed(), run.getAlreadyApplied(), run.getBelowScore(),
				run.getDeferred(), run.getMessage(), run.getStartedAt(), run.getFinishedAt());
	}

	public ApplicationSummaryResponse toSummary(Application a) {
		return new ApplicationSummaryResponse(a.getId(), a.getJobId(), a.getJobTitle(), a.getJobCompany(),
				a.getJobLocation(), a.getApplyUrl(), a.getMatchScore(), a.getStatus(), a.getRiskBand(),
				a.getSubmittedVia(), a.getCreatedAt(), a.getUpdatedAt());
	}

	public ApplicationDetailResponse toDetail(Application a, List<ApplicationEvent> events) {
		return new ApplicationDetailResponse(toSummary(a), a.getRiskReason(), a.getNeedsYouReason(), a.getAttempts(),
				a.getLastError(), a.getNextAttemptAt(), prefill(a),
				events.stream().map(e -> new ApplicationEventResponse(e.getFromStatus(), e.getToStatus(), e.getNote(), e.getAt()))
						.toList());
	}

	public NeedsYouResponse toNeedsYou(Application a) {
		return new NeedsYouResponse(toSummary(a), a.getNeedsYouReason(), prefill(a));
	}

	private Map<String, String> prefill(Application a) {
		return a.getPrefill() == null ? Map.of() : jsonMapper.readValue(a.getPrefill(), PREFILL);
	}

}
