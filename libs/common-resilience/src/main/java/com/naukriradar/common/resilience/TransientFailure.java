package com.naukriradar.common.resilience;

/**
 * Marks an exception as worth retrying: the other side may well answer next time (a 5xx, a
 * timeout, a dropped connection). Anything else, like a 400 or a reply we can't parse, would
 * fail the same way again, so it is not retried and doesn't count against the circuit.
 */
public interface TransientFailure {
}
