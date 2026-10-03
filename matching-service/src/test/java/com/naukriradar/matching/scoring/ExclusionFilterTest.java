package com.naukriradar.matching.scoring;

import org.junit.jupiter.api.Test;

import static com.naukriradar.matching.support.TestData.job;
import static com.naukriradar.matching.support.TestData.profile;
import static org.assertj.core.api.Assertions.assertThat;

class ExclusionFilterTest {

	@Test
	void excludedCompanyIsDroppedButOnlyAsAWholeWord() {
		ExclusionFilter filter = new ExclusionFilter(profile().excludeCompanies("meta").build());

		assertThat(filter.reason(job().company("Meta Platforms Inc").build())).isEqualTo("company meta");
		assertThat(filter.reason(job().company("Metaflow Labs").build())).isNull();
	}

	@Test
	void excludedKeywordInTitleOrDescriptionDropsTheJob() {
		ExclusionFilter filter = new ExclusionFilter(profile().excludeKeywords("unpaid", "night shift").build());

		assertThat(filter.reason(job().title("Unpaid Intern").build())).isEqualTo("keyword unpaid");
		assertThat(filter.reason(job().description("Rotational NIGHT SHIFT required").build())).isEqualTo("keyword night shift");
		assertThat(filter.reason(job().description("Shifts at night are rare").build())).isNull();
	}

	@Test
	void nothingExcludedKeepsEverything() {
		assertThat(new ExclusionFilter(profile().build()).reason(job().build())).isNull();
	}

}
