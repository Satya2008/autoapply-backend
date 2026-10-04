package com.naukriradar.core.scheduler;

import java.util.List;

import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.core.repository.ProfileRepository;
import com.naukriradar.core.service.ApplyRunService;
import com.naukriradar.core.settings.SettingDefinitions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs applying for every user who turned auto apply on, one user at a time on the job's
 * own thread. One user's failure is recorded on their run and doesn't stop the others.
 */
@Component
public class AutoApplyJob implements ScheduledJob {

	private static final Logger log = LoggerFactory.getLogger(AutoApplyJob.class);

	private final ProfileRepository profileRepository;
	private final ApplyRunService runService;

	public AutoApplyJob(ProfileRepository profileRepository, ApplyRunService runService) {
		this.profileRepository = profileRepository;
		this.runService = runService;
	}

	@Override
	public String name() {
		return "auto-apply";
	}

	@Override
	public String cronSetting() {
		return SettingDefinitions.AUTO_APPLY_CRON;
	}

	@Override
	public String enabledSetting() {
		return SettingDefinitions.AUTO_APPLY_ENABLED;
	}

	@Override
	public String run() {
		List<String> users = profileRepository.findUserIdsWithAutoApply();
		int done = 0;
		int busy = 0;
		int failed = 0;
		for (String userId : users) {
			if (Thread.currentThread().isInterrupted()) {
				break;
			}
			try {
				runService.runNow(userId);
				done++;
			}
			catch (ConflictException ex) {
				busy++;
			}
			catch (RuntimeException ex) {
				failed++;
				log.error("Auto apply for user {} failed", userId, ex);
			}
		}
		return "Ran for " + done + " of " + users.size() + " users; " + busy + " already running, " + failed + " failed.";
	}

}
