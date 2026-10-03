package com.naukriradar.core.model;

import com.naukriradar.core.exception.IllegalTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationStatusTest {

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({
			"PLANNED, QUEUED", "PLANNED, NEEDS_YOU", "QUEUED, SIMULATED", "QUEUED, FAILED", "FAILED, QUEUED",
			"FAILED, NEEDS_YOU", "NEEDS_YOU, APPLIED", "NEEDS_YOU, SKIPPED", "SIMULATED, APPLIED",
			"APPLIED, INTERVIEW", "SUBMITTED, REJECTED", "INTERVIEW, OFFER", "INTERVIEW, REJECTED" })
	void allowedMoves(ApplicationStatus from, ApplicationStatus to) {
		assertThat(from.canMoveTo(to)).isTrue();
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({
			"SKIPPED, OFFER", "SKIPPED, APPLIED", "OFFER, REJECTED", "REJECTED, INTERVIEW", "NEEDS_YOU, QUEUED",
			"PLANNED, APPLIED", "APPLIED, NEEDS_YOU", "NEEDS_YOU, INTERVIEW", "SIMULATED, INTERVIEW" })
	void forbiddenMoves(ApplicationStatus from, ApplicationStatus to) {
		assertThat(from.canMoveTo(to)).isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = ApplicationStatus.class, names = { "OFFER", "REJECTED", "SKIPPED" })
	void finalStatusesGoNowhere(ApplicationStatus status) {
		assertThat(status.isFinal()).isTrue();
	}

	@Test
	void theEntityRefusesAnIllegalMove() {
		Application application = application(RiskBand.LOW);
		application.moveTo(ApplicationStatus.NEEDS_YOU);
		application.moveTo(ApplicationStatus.SKIPPED);

		assertThatThrownBy(() -> application.moveTo(ApplicationStatus.OFFER))
				.isInstanceOf(IllegalTransitionException.class)
				.hasMessageContaining("SKIPPED to OFFER");
	}

	@ParameterizedTest
	@EnumSource(value = RiskBand.class, names = { "MEDIUM", "HIGH" })
	void onlyLowRiskCanEverBeQueued(RiskBand risk) {
		Application application = application(risk);

		assertThatThrownBy(() -> application.moveTo(ApplicationStatus.QUEUED))
				.isInstanceOf(IllegalTransitionException.class)
				.hasMessageContaining(risk.name());
		assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PLANNED);
	}

	private static Application application(RiskBand risk) {
		return new Application("u", "j", "Engineer", "Acme", "Pune", "https://jobs.example.com/1", 80, risk, "test", "{}");
	}

}
