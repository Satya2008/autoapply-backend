package com.naukriradar.job.support;

import com.naukriradar.job.model.JobSource;
import com.naukriradar.job.model.RequestMethod;
import com.naukriradar.job.model.SourceType;

public final class TestSources {

	private TestSources() {
	}

	/** An enabled GET source with no mappings yet; tests add what they need. */
	public static JobSource source(String code, String baseUrl, String searchPath, String resultsPath) {
		JobSource source = new JobSource(code);
		source.setName(code);
		source.setType(SourceType.REST_JSON);
		source.setBaseUrl(baseUrl);
		source.setSearchPath(searchPath);
		source.setMethod(RequestMethod.GET);
		source.setResultsPath(resultsPath);
		source.setEnabled(true);
		source.setTimeoutSeconds(5);
		source.setMaxPages(1);
		return source;
	}

}
