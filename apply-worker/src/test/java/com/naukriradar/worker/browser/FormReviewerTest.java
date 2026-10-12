package com.naukriradar.worker.browser;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FormReviewerTest {

	@Test
	void choosesTheOptionThatFitsAndNeverJustTheFirst() {
		List<String> schools = List.of("Mahatma Gandhi University", "Other");

		assertThat(FormReviewer.bestOption(List.of("India", "Indonesia"), "India")).isZero();
		assertThat(FormReviewer.bestOption(List.of("Bengaluru, Karnataka, India"), "Bengaluru")).isZero();
		assertThat(FormReviewer.bestOption(schools, "Mahatma Gandhi Kashi Vidyapith")).isEqualTo(-1);
		assertThat(FormReviewer.bestOption(List.of("No options"), "Sociology")).isEqualTo(-1);
		assertThat(FormReviewer.bestOption(List.of("Bachelor’s Degree", "Master’s Degree"), "Master's Degree")).isEqualTo(1);
	}

	@Test
	void findsApplyButtonsAsSitesWriteThem() {
		assertThat(FormReviewer.isApplyButton("Apply")).isTrue();
		assertThat(FormReviewer.isApplyButton("Apply Now")).isTrue();
		assertThat(FormReviewer.isApplyButton("Apply \n(opens in new window)")).isTrue();
		assertThat(FormReviewer.isApplyButton("Apply for this job")).isTrue();
		assertThat(FormReviewer.isApplyButton("Apply filters")).isFalse();
		assertThat(FormReviewer.isApplyButton("How to apply")).isFalse();
	}

	@Test
	void namesDegreesTheWayDegreeListsDo() {
		assertThat(FormReviewer.degreeLevel("M.A.")).isEqualTo("Master's Degree");
		assertThat(FormReviewer.degreeLevel("MCA")).isEqualTo("Master's Degree");
		assertThat(FormReviewer.degreeLevel("B.Tech")).isEqualTo("Bachelor's Degree");
		assertThat(FormReviewer.degreeLevel("PhD")).isEqualTo("Doctor");
		assertThat(FormReviewer.degreeLevel("Class XII")).isNull();
	}

}
