package io.github.hugrlab.acl.jdbc;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a connection's settings into a bearer: which issuer (the door's discovery, or the
 * connection's {@code issuer}/{@code clientId}), then a cached access token, a silent refresh, or the
 * flow itself - in that order. Acquisitions for the same sign-in run one at a time, so the several
 * connections a tool opens at once share one browser window, not one each.
 */
final class TokenProvider {
	private static final Map<String, Object> LOCKS = new ConcurrentHashMap<>();

	private final Discovery discovery;
	private final Interaction interaction;

	TokenProvider(Discovery discovery, Interaction interaction) {
		this.discovery = discovery;
		this.interaction = interaction;
	}

	String acquire(AclConfig config) throws AuthException {
		AclConfig.Flow flow = config.effectiveFlow(interaction.browserAvailable());
		if (flow == AclConfig.Flow.TOKEN) {
			if (config.token == null) {
				throw new AuthException("flow=token needs the token property");
			}
			return config.token;
		}
		if (flow == AclConfig.Flow.PASSWORD && (config.user == null || config.password == null)) {
			throw new AuthException("flow=password needs user and password");
		}
		String flowName = flowName(flow);
		DoorIssuer chosen = chooseIssuer(config, flowName);
		String clientId = config.clientId != null ? config.clientId : chooseClient(config, chosen, flowName);
		Oidc oidc = endpoints(chosen);
		// a password sign-in is keyed by the password too - a wrong password never gets the right one's
		// token from the cache - and kept in memory only: not even a hash of a password goes to disk
		boolean password = flow == AclConfig.Flow.PASSWORD;
		String who = password ? "user:" + config.user + ":" + sha256(config.password) : "interactive";
		String key = TokenCache.key(chosen.issuer(), clientId, who);
		TokenCache cache = password && config.cache == AclConfig.Cache.FILE ? new TokenCache.Memory()
		                                                                     : TokenCache.of(config);
		synchronized (LOCKS.computeIfAbsent(key, k -> new Object())) {
			long now = Flows.now();
			Tokens cached = cache.get(key);
			if (cached != null && cached.accessUsable(now)) {
				return cached.accessToken();
			}
			if (cached != null && cached.refreshUsable(now)) {
				try {
					Tokens refreshed = Flows.refresh(oidc, clientId, cached);
					cache.put(key, refreshed);
					return refreshed.accessToken();
				} catch (AuthException e) {
					cache.remove(key); // a refused refresh (session ended at the IdP): sign in again
				}
			}
			Tokens fresh;
			switch (flow) {
			case PASSWORD:
				fresh = Flows.password(oidc, clientId, config.scope, config.user, config.password);
				break;
			case DEVICE:
				fresh = Flows.device(oidc, clientId, config.scope, interaction, config.loginTimeoutSeconds);
				break;
			default:
				fresh = Flows.authorizationCode(oidc, clientId, config.scope, interaction, config.redirectPort,
				    config.loginTimeoutSeconds);
			}
			cache.put(key, fresh);
			return fresh.accessToken();
		}
	}

	/**
	 * The flow as the door names it in a client's {@code flows}. The driver's own password sign-in is
	 * the IdP's password grant run by the driver - a client that runs the browser or device sign-in is
	 * a public client of that IdP, which is what the grant needs.
	 */
	private static String flowName(AclConfig.Flow flow) {
		return flow == AclConfig.Flow.DEVICE ? "device" : "authcode";
	}

	/**
	 * The connection's issuer when it names one (by URL or by the door's name), else the first issuer
	 * the door trusts that has a client for this flow, else the first with any client.
	 */
	private DoorIssuer chooseIssuer(AclConfig config, String flow) throws AuthException {
		List<DoorIssuer> issuers = List.of();
		if (config.discovery) {
			try {
				issuers = discovery.discover(config);
			} catch (IOException e) {
				if (config.issuer == null) {
					throw new AuthException("could not ask the door which issuer to sign in with (" + e.getMessage()
					                            + ") - set the issuer and clientId properties",
					    e);
				}
			}
		}
		if (config.issuer != null) {
			for (DoorIssuer issuer : issuers) {
				if (sameIssuer(issuer.issuer(), config.issuer) || config.issuer.equals(issuer.name())) {
					return issuer;
				}
			}
			if (!issuers.isEmpty()) {
				throw new AuthException("the door does not trust the issuer " + config.issuer + "; it trusts "
				                        + issuers.stream().map(DoorIssuer::issuer).toList());
			}
			return DoorIssuer.configured(config.issuer);
		}
		if (config.clientId != null && !issuers.isEmpty()) {
			return issuers.get(0); // the connection's own client: any issuer the door names will do
		}
		for (DoorIssuer issuer : issuers) {
			if (issuer.clients().stream().anyMatch(c -> c.runs(flow))) {
				return issuer;
			}
		}
		for (DoorIssuer issuer : issuers) {
			if (!issuer.clients().isEmpty()) {
				return issuer;
			}
		}
		if (!issuers.isEmpty()) {
			return issuers.get(0);
		}
		throw new AuthException("the door names no issuer to sign in with - set the issuer and clientId properties, "
		                        + "or connect with a token");
	}

	/** The connection's client by name, else the issuer's first client that runs the flow, else its first. */
	private static String chooseClient(AclConfig config, DoorIssuer issuer, String flow) throws AuthException {
		if (config.client != null) {
			for (DoorIssuer.DoorClient client : issuer.clients()) {
				if (config.client.equals(client.name())) {
					return client.clientId();
				}
			}
			throw new AuthException("the door names no client " + config.client + " for " + issuer.issuer()
			                        + "; it names " + issuer.clients().stream().map(DoorIssuer.DoorClient::name).toList());
		}
		for (DoorIssuer.DoorClient client : issuer.clients()) {
			if (client.runs(flow)) {
				return client.clientId();
			}
		}
		if (!issuer.clients().isEmpty()) {
			return issuer.clients().get(0).clientId();
		}
		throw new AuthException("the door names no client for " + issuer.issuer()
		                        + " - set the clientId property (a public OIDC client of that issuer)");
	}

	private static Oidc endpoints(DoorIssuer issuer) throws AuthException {
		try {
			return Oidc.discover(issuer.issuer());
		} catch (IOException e) {
			if (issuer.tokenEndpoint() != null) {
				// the door's own copy of the endpoints: enough for password and device, not the browser
				return new Oidc(issuer.issuer(), issuer.authorizationEndpoint(), issuer.tokenEndpoint(),
				    issuer.deviceEndpoint());
			}
			throw new AuthException("could not read the endpoints of " + issuer.issuer() + ": " + e.getMessage(), e);
		}
	}

	private static String sha256(String text) {
		try {
			byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
			                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static boolean sameIssuer(String a, String b) {
		return strip(a).equals(strip(b));
	}

	private static String strip(String s) {
		return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
	}
}
