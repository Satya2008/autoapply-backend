package com.naukriradar.job.provider;

import java.util.List;

import com.naukriradar.job.model.JobSource;
import com.naukriradar.job.model.SourceType;

/**
 * Reads one page of jobs from a board. One implementation per {@link SourceType}; a board
 * that needs GraphQL or scraping later gets its own.
 */
public interface JobSourceProvider {

	SourceType supports();

	/**
	 * @return the page's items, empty when the board has no more
	 * @throws com.naukriradar.job.exception.JobSourceFetchException if the call or the
	 * response handling fails
	 */
	List<RawJob> fetchPage(JobSource source, FetchRequest request);

}
