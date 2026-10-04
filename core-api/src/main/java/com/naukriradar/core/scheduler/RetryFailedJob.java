package com.naukriradar.core.scheduler;

import java.time.Instant;
import java.util.List;

import com.naukriradar.core.model.ApplicationStatus;
import com.naukriradar.core.repository.ApplicationRepository;
import com.naukriradar.core.service.ApplyExecutor;
import com.naukriradar.core.settings.SettingDefinitions;
import org.springframework.stereotype.Component;

/** Sends failed automatic applications again once their wait is over, without waiting for the next run. */
@Component
public class RetryFailedJob implements ScheduledJob {

	private final ApplicationRepository applicationRepository;
	private final ApplyExecutor executor;

	public RetryFailedJob(ApplicationRepository applicationRepository, ApplyExecutor executor) {
		this.applicationRepository = applicationRepository;
		this.executor = executor;
	}

	@Override
	public String name() {
		return "retry-failed";
	}

	@Override
	public String cronSetting() {
		return SettingDefinitions.RETRY_FAILED_CRON;
	}

	@Override
	public String enabledSetting() {
		return SettingDefinitions.RETRY_FAILED_ENABLED;
	}

	@Override
	public String run() {
		List<String> users = applicationRepository.findUserIdsWithStatusDue(ApplicationStatus.FAILED, Instant.now());
		int sent = 0;
		int failed = 0;
		int handedOver = 0;
		for (String userId : users) {
			ApplyExecutor.Outcome outcome = executor.process(executor.requeueDue(userId));
			sent += outcome.sent();
			failed += outcome.failed();
			handedOver += outcome.handedOver();
		}
		return "Retried for " + users.size() + " users: " + sent + " sent, " + failed + " failed again, "
				+ handedOver + " handed to the user.";
	}

}
