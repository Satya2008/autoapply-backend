package com.naukriradar.common.redis;

import java.lang.management.ManagementFactory;
import java.util.UUID;

/**
 * Names this running process. Used to ignore our own pub/sub messages and to show who holds
 * a lock. The pid@host part is for people reading Redis; the random part keeps two
 * processes on one machine apart.
 */
public record InstanceId(String value) {

	public static InstanceId create() {
		String runtime = ManagementFactory.getRuntimeMXBean().getName();
		return new InstanceId(runtime + "/" + UUID.randomUUID().toString().substring(0, 8));
	}

	@Override
	public String toString() {
		return value;
	}

}
