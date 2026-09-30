package io.github.hugrlab.acl.jdbc;

import java.util.Map;

/**
 * What a token endpoint answered: the access token with its expiry, and the refresh token when the
 * IdP issued one. Times are epoch seconds; 0 means unknown.
 */
record Tokens(String accessToken, long expiresAt, String refreshToken, long refreshExpiresAt) {
	/** How long before its expiry an access token stops being handed out. */
	static final long MARGIN_SECONDS = 30;

	static Tokens from(Http.Response response, long now) {
		long expiresIn = response.number("expires_in", 0);
		long refreshIn = response.number("refresh_expires_in", 0);
		return new Tokens(response.text("access_token"), expiresIn > 0 ? now + expiresIn : 0,
		    response.text("refresh_token"), refreshIn > 0 ? now + refreshIn : 0);
	}

	boolean accessUsable(long now) {
		return accessToken != null && expiresAt > 0 && expiresAt - MARGIN_SECONDS > now;
	}

	boolean refreshUsable(long now) {
		return refreshToken != null && (refreshExpiresAt == 0 || refreshExpiresAt - MARGIN_SECONDS > now);
	}

	/** A refresh answer may omit the refresh token: the old one then stays in use. */
	Tokens keepingRefresh(Tokens previous) {
		if (refreshToken != null || previous == null) {
			return this;
		}
		return new Tokens(accessToken, expiresAt, previous.refreshToken, previous.refreshExpiresAt);
	}

	Map<String, Object> toJson() {
		return Map.of("access_token", accessToken == null ? "" : accessToken, "expires_at", expiresAt,
		    "refresh_token", refreshToken == null ? "" : refreshToken, "refresh_expires_at", refreshExpiresAt);
	}

	static Tokens fromJson(Map<?, ?> m) {
		return new Tokens(blank(m.get("access_token")), number(m.get("expires_at")), blank(m.get("refresh_token")),
		    number(m.get("refresh_expires_at")));
	}

	private static String blank(Object o) {
		return o == null || o.toString().isEmpty() ? null : o.toString();
	}

	private static long number(Object o) {
		return o instanceof Number n ? n.longValue() : 0;
	}

	@Override
	public String toString() {
		return "Tokens[expiresAt=" + expiresAt + ", refresh=" + (refreshToken != null) + "]"; // never the tokens
	}
}
