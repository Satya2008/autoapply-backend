package com.naukriradar.matching.scoring;

import java.util.List;

/** A job's 0-100 score and how each factor contributed. */
public record ScoreResult(int total, List<FactorScore> breakdown) {
}
