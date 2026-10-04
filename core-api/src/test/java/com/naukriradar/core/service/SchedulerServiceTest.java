package com.naukriradar.core.service;

import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.core.config.ApplicationProperties;
import com.naukriradar.core.dto.response.ScheduledJobResponse;
import com.naukriradar.core.scheduler.ScheduledJob;
import com.naukriradar.core.settings.SettingChangedEvent;
import com.naukriradar.core.settings.SettingDefinitions;
import com.naukriradar.core.support.FixedSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchedulerServiceTest {

	private final FixedSettings settings = new FixedSettings();

	private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();

	private final BlockingJob job = new BlockingJob();

	private SchedulerService scheduler;

	@BeforeEach
	void setUp() {
		taskScheduler.setPoolSize(2);
		taskScheduler.initialize();
		scheduler = new SchedulerService(taskScheduler, List.of(job), settings,
				new ApplicationProperties(ApplicationProperties.ApplyMode.SIMULATE, ZoneId.of("Asia/Kolkata"), 2, 20), true);
		scheduler.start();
	}

	@AfterEach
	void tearDown() {
		job.release.countDown();
		taskScheduler.shutdown();
	}

	@Test
	void jobIsScheduledFromItsCronSetting() {
		ScheduledJobResponse view = only();

		assertThat(view.scheduled()).isTrue();
		assertThat(view.cron()).isEqualTo("0 0 10 * * *");
		assertThat(view.nextRunAt()).isNotNull();
	}

	@Test
	void changingTheCronMovesTheJobWithoutARestart() {
		var before = only().nextRunAt();

		settings.with(SettingDefinitions.AUTO_APPLY_CRON, "0 15 3 1 1 *");
		scheduler.onSettingChanged(new SettingChangedEvent(SettingDefinitions.AUTO_APPLY_CRON));

		assertThat(only().cron()).isEqualTo("0 15 3 1 1 *");
		assertThat(only().nextRunAt()).isNotEqualTo(before);
	}

	@Test
	void turningAJobOffUnschedulesItAndOnAgainBringsItBack() {
		settings.with(SettingDefinitions.AUTO_APPLY_ENABLED, "false");
		scheduler.onSettingChanged(new SettingChangedEvent(SettingDefinitions.AUTO_APPLY_ENABLED));
		assertThat(only().scheduled()).isFalse();
		assertThat(only().nextRunAt()).isNull();

		settings.with(SettingDefinitions.AUTO_APPLY_ENABLED, "true");
		scheduler.onSettingChanged(new SettingChangedEvent(SettingDefinitions.AUTO_APPLY_ENABLED));
		assertThat(only().scheduled()).isTrue();
	}

	@Test
	void unrelatedSettingsLeaveTheScheduleAlone() {
		scheduler.onSettingChanged(new SettingChangedEvent(SettingDefinitions.MAX_ATTEMPTS));

		assertThat(only().scheduled()).isTrue();
	}

	@Test
	void runNowRunsInTheBackgroundAndRefusesASecondRunMeanwhile() throws Exception {
		scheduler.runNow("auto-apply");
		assertThat(job.started.await(5, TimeUnit.SECONDS)).isTrue();

		assertThat(only().running()).isTrue();
		assertThatThrownBy(() -> scheduler.runNow("auto-apply")).isInstanceOf(ConflictException.class);

		job.release.countDown();
		waitUntil(() -> !only().running());
		assertThat(only().lastSuccess()).isTrue();
		assertThat(only().lastResult()).isEqualTo("done");
		assertThat(only().lastTrigger()).isEqualTo("manual");
	}

	@Test
	void aFailingJobIsRecordedNotThrown() throws Exception {
		job.fail = true;
		job.release.countDown();

		scheduler.runNow("auto-apply");
		waitUntil(() -> only().lastSuccess() != null);

		assertThat(only().lastSuccess()).isFalse();
		assertThat(only().lastResult()).contains("boom");
	}

	@Test
	void unknownJobIsNotFound() {
		assertThatThrownBy(() -> scheduler.runNow("nope")).isInstanceOf(NotFoundException.class);
	}

	private ScheduledJobResponse only() {
		return scheduler.list().get(0);
	}

	private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
		for (int i = 0; i < 100 && !condition.getAsBoolean(); i++) {
			Thread.sleep(50);
		}
		assertThat(condition.getAsBoolean()).isTrue();
	}

	/** Uses the auto-apply settings; waits until the test lets it finish. */
	private static final class BlockingJob implements ScheduledJob {

		final CountDownLatch started = new CountDownLatch(1);

		final CountDownLatch release = new CountDownLatch(1);

		volatile boolean fail;

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
			started.countDown();
			try {
				release.await(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			if (fail) {
				throw new IllegalStateException("boom");
			}
			return "done";
		}

	}

}
