package com.naukriradar.job.provider;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

import com.naukriradar.job.config.JobsProperties;
import com.naukriradar.job.exception.JobSourceFetchException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Stops source URLs from reaching internal addresses (localhost, 10.x, 192.168.x, cloud
 * metadata at 169.254.x). Source URLs are typed by admins, but a mistake or a stolen admin
 * account shouldn't turn this service into a way to probe the private network.
 */
@Component
@RequiredArgsConstructor
public class HostGuard {

	private final JobsProperties properties;

	public void check(URI uri) {
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!scheme.equals("http") && !scheme.equals("https")) {
			throw new JobSourceFetchException("Only http and https URLs are allowed.");
		}
		if (uri.getHost() == null) {
			throw new JobSourceFetchException("The URL has no host.");
		}
		if (properties.allowPrivateHosts()) {
			return;
		}
		try {
			for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
				if (isInternal(address)) {
					throw new JobSourceFetchException("Host " + uri.getHost() + " resolves to a private address.");
				}
			}
		}
		catch (UnknownHostException ex) {
			throw new JobSourceFetchException("Unknown host " + uri.getHost() + ".", ex);
		}
	}

	private static boolean isInternal(InetAddress address) {
		return address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
				|| address.isAnyLocalAddress() || address.isMulticastAddress()
				// IPv6 unique local addresses (fc00::/7) aren't covered by isSiteLocalAddress
				|| (address.getAddress().length == 16 && (address.getAddress()[0] & 0xFE) == 0xFC);
	}

}
