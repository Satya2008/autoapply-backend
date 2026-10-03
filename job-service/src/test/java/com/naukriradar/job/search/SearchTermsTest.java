package com.naukriradar.job.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTermsTest {

	@Test
	void plainWordsBecomeRequiredFullTextTerms() {
		SearchTerms terms = SearchTerms.parse("  Java   Spring ");

		assertThat(terms.fullText()).isEqualTo("+java +spring");
		assertThat(terms.titlePatterns()).isEmpty();
	}

	@Test
	void booleanOperatorsInUserInputAreNeverPassedThrough() {
		SearchTerms terms = SearchTerms.parse("+java -spring \"kafka\" (redis) ~docker* @aws <x>");

		assertThat(terms.fullText()).isEqualTo("+java +spring +kafka +redis +docker +aws");
	}

	@Test
	void shortAndSymbolTermsBecomeTitlePatterns() {
		SearchTerms terms = SearchTerms.parse("go c# c++ .net node.js java");

		assertThat(terms.fullText()).isEqualTo("+java");
		assertThat(terms.titlePatterns()).containsExactly(
				"(^|[^[:alnum:]])go([^[:alnum:]]|$)",
				"(^|[^[:alnum:]])c#([^[:alnum:]]|$)",
				"(^|[^[:alnum:]])c\\+\\+([^[:alnum:]]|$)",
				"(^|[^[:alnum:]])\\.net([^[:alnum:]]|$)",
				"(^|[^[:alnum:]])node\\.js([^[:alnum:]]|$)");
	}

	@Test
	void stopwordsAndPurePunctuationAreDropped() {
		SearchTerms terms = SearchTerms.parse("the developer for !!! ---");

		assertThat(terms.fullText()).isEqualTo("+developer");
		assertThat(terms.titlePatterns()).isEmpty();
	}

	@Test
	void repeatsAreRemovedAndTheTermCountIsCapped() {
		SearchTerms terms = SearchTerms.parse("java JAVA java " + "word ".repeat(30) + "a b c d e f g h i j k l");

		assertThat(terms.fullText()).isEqualTo("+java +word");
		assertThat(terms.titlePatterns()).hasSizeLessThanOrEqualTo(SearchTerms.MAX_TERMS - 2);
	}

	@Test
	void blankQueryMeansNoFilter() {
		assertThat(SearchTerms.parse("   ").fullText()).isNull();
		assertThat(SearchTerms.parse(null).titlePatterns()).isEmpty();
	}

}
