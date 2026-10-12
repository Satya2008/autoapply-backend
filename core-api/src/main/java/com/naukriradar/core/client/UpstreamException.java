package com.naukriradar.core.client;

import com.naukriradar.common.exception.ServiceUnavailableException;

/**
 * Another service couldn't give us what a run needs. The message is shown to the user; a
 * request that ends with it answers 503.
 */
public class UpstreamException extends ServiceUnavailableException {

	public UpstreamException(String message, Throwable cause) {
		super(message, cause);
	}

}
