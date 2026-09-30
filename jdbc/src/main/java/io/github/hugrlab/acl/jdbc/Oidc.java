package io.github.hugrlab.acl.jdbc;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** An issuer's endpoints, from its {@code .well-known/openid-configuration}; cached five minutes. */
record Oidc(String issuer, String authorizationEndpoint, String tokenEndpoint, String deviceEndpoint) {
	private static final long TTL_MILLIS = 300_000;
	private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

	private record Cached(Oidc endpoints, long at) {
	}

	static Oidc discover(String issuer) throws IOException {
		String base = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
		Cached cached = CACHE.get(base);
		if (cached != null && System.currentTimeMillis() - cached.at < TTL_MILLIS) {
			return cached.endpoints;
		}
		Http.Response doc = Http.get(base + "/.well-known/openid-configuration");
		if (!doc.ok()) {
			throw new IOException("OIDC discovery at " + base + " answered " + doc.error());
		}
		Oidc endpoints = new Oidc(issuer, doc.text("authorization_endpoint"), doc.text("token_endpoint"),
		    doc.text("device_authorization_endpoint"));
		if (endpoints.tokenEndpoint == null) {
			throw new IOException("OIDC discovery at " + base + " names no token_endpoint");
		}
		CACHE.put(base, new Cached(endpoints, System.currentTimeMillis()));
		return endpoints;
	}
}
