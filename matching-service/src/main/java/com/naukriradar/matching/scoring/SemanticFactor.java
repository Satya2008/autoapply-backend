package com.naukriradar.matching.scoring;

import com.naukriradar.matching.client.CandidateJob;
import com.naukriradar.matching.embedding.Vectors;
import org.springframework.stereotype.Component;

/**
 * How close the job's meaning is to the profile's, by the cosine of their embeddings. Catches
 * what words miss: a "Spring Microservices Engineer" posting for a "Java Backend" profile.
 * Without vectors it says "can't tell", so a run without semantic matching scores as before.
 */
@Component
public class SemanticFactor implements ScoringFactor {

	@Override
	public String name() {
		return "semantic";
	}

	@Override
	public FactorResult score(MatchContext context, CandidateJob job) {
		SemanticVectors semantic = context.semantic();
		if (!semantic.on()) {
			return FactorResult.unknown("Semantic matching is off.");
		}
		float[] vector = semantic.jobs().get(job.id());
		if (vector == null || vector.length == 0) {
			return FactorResult.unknown("This job has no meaning vector yet.");
		}
		double cosine = Vectors.cosine(semantic.profile(), vector);
		double score = semantic.range().scale(cosine);
		return new FactorResult(score, "In meaning, " + Math.round(score * 100) + "% close to your roles and skills.");
	}

}
