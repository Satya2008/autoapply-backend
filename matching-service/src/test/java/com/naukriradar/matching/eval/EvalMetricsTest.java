package com.naukriradar.matching.eval;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvalMetricsTest {

	@Test
	void perfectPredictionsScorePerfectly() {
		EvalMetrics metrics = EvalMetrics.of(List.of(90, 20, 70, 10), List.of(90, 20, 70, 10), 60);

		assertThat(metrics.mae()).isZero();
		assertThat(metrics.within15()).isEqualTo(1.0);
		assertThat(metrics.spearman()).isEqualTo(1.0);
		assertThat(metrics.precision()).isEqualTo(1.0);
		assertThat(metrics.recall()).isEqualTo(1.0);
		assertThat(metrics.f1()).isEqualTo(1.0);
	}

	@Test
	void theNumbersMeasureMissOrderAndMatches() {
		// expected matches: the first two; predicted: the first and the third
		EvalMetrics metrics = EvalMetrics.of(List.of(90, 70, 20, 10), List.of(80, 40, 65, 0), 60);

		assertThat(metrics.mae()).isEqualTo(23.75);
		assertThat(metrics.within15()).isEqualTo(0.5);
		assertThat(metrics.precision()).isEqualTo(0.5);
		assertThat(metrics.recall()).isEqualTo(0.5);
		assertThat(metrics.spearman()).isEqualTo(0.8);
	}

	@Test
	void reversedOrderIsMinusOneAndTiesShareARank() {
		assertThat(EvalMetrics.spearman(List.of(1, 2, 3), List.of(30, 20, 10))).isEqualTo(-1.0);
		assertThat(EvalMetrics.spearman(List.of(1, 2, 2, 3), List.of(1, 2, 2, 3))).isEqualTo(1.0);
		assertThat(EvalMetrics.spearman(List.of(5), List.of(5))).isNull();
		assertThat(EvalMetrics.spearman(List.of(5, 5), List.of(1, 2))).isNull();
	}

	@Test
	void edgeCases() {
		assertThat(EvalMetrics.of(List.of(), List.of(), 60).cases()).isZero();
		assertThat(EvalMetrics.of(List.of(10, 20), List.of(15, 25), 60).recall()).isZero();
		assertThatThrownBy(() -> EvalMetrics.of(List.of(1), List.of(), 60)).isInstanceOf(IllegalArgumentException.class);
	}

}
