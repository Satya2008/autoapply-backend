package com.autoapply.jobs.source;

import com.autoapply.jobs.entity.Job;

import java.util.List;

public interface JobSourceProvider {

    /** Matches JobSourceConfig.providerType. */
    String type();

    List<Job> fetch(JobSourceConfig config, String query, String country, int page);

    default boolean supports(JobSourceConfig config) {
        return type().equalsIgnoreCase(config.getProviderType());
    }
}
