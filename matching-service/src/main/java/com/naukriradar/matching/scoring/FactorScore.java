package com.naukriradar.matching.scoring;

/**
 * One line of a match's breakdown, stored with the match so the user can see why it scored
 * what it did.
 *
 * @param score the factor's own 0-1 verdict
 * @param points what it added to the 0-100 total
 */
public record FactorScore(String factor, int weight, double score, double points, String detail) {
}
