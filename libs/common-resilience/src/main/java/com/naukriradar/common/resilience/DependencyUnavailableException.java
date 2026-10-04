package com.naukriradar.common.resilience;

/**
 * A call was not made: the circuit for that dependency is open (it failed a lot just now) or
 * too many calls to it are already in flight.
 */
public class DependencyUnavailableException extends RuntimeException {

	private final String dependency;

	public DependencyUnavailableException(String dependency, String reason, Throwable cause) {
		super(dependency + " is unavailable: " + reason, cause);
		this.dependency = dependency;
	}

	public String dependency() {
		return dependency;
	}

}
