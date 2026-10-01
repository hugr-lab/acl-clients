package io.github.hugrlab.acl.jdbc;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * One connection's settings: {@code jdbc:acl://host:port[?key=value&...]} plus the JDBC properties
 * (a property wins over the same key in the URL). The keys this driver owns are consumed here; every
 * other key is handed to Arrow's Flight SQL driver untouched ({@code useEncryption},
 * {@code disableCertificateVerification}, {@code tlsRootCerts}, {@code trustStore}, ...).
 */
final class AclConfig {
	static final String PREFIX = "jdbc:acl://";
	static final String DELEGATE_PREFIX = "jdbc:arrow-flight-sql://";

	/** How the driver obtains the token. {@code AUTO} picks one from what the connection supplies. */
	enum Flow {
		AUTO, AUTHCODE, DEVICE, PASSWORD, TOKEN
	}

	/** Where acquired tokens are kept between connections. */
	enum Cache {
		MEMORY, FILE, NONE
	}

	// the keys this driver owns: documented in getPropertyInfo and never passed on
	static final String FLOW = "flow";
	static final String ISSUER = "issuer";
	static final String CLIENT_ID = "clientId";
	static final String CLIENT = "client";
	static final String SCOPE = "scope";
	static final String TOKEN = "token";
	static final String USER = "user";
	static final String PASSWORD = "password";
	static final String TOKEN_CACHE = "tokenCache";
	static final String TOKEN_CACHE_FILE = "tokenCacheFile";
	static final String LOGIN_TIMEOUT = "loginTimeout";
	static final String REDIRECT_PORT = "redirectPort";
	static final String DISCOVERY = "discovery";
	static final Set<String> OWN = Set.of(FLOW, ISSUER, CLIENT_ID, CLIENT, SCOPE, TOKEN, USER, PASSWORD, TOKEN_CACHE,
	    TOKEN_CACHE_FILE, LOGIN_TIMEOUT, REDIRECT_PORT, DISCOVERY);

	// read by the discovery handshake as well as passed on: the door is one TLS endpoint for both
	static final String USE_ENCRYPTION = "useEncryption";
	static final String DISABLE_CERT_VERIFICATION = "disableCertificateVerification";
	static final String TLS_ROOT_CERTS = "tlsRootCerts";

	final String host;
	final int port;
	final Flow flow;
	final String issuer;
	final String clientId;
	final String client;
	final String scope;
	final String token;
	final String user;
	final String password;
	final Cache cache;
	final Path cacheFile;
	final int loginTimeoutSeconds;
	final int redirectPort;
	final boolean discovery;
	final boolean useEncryption;
	final boolean disableCertificateVerification;
	final String tlsRootCerts;
	private final Properties passthrough;

	private AclConfig(String host, int port, Properties all) throws SQLException {
		this.host = host;
		this.port = port;
		this.flow = parseEnum(Flow.class, all.getProperty(FLOW, "auto"), FLOW);
		this.issuer = blankToNull(all.getProperty(ISSUER));
		this.clientId = blankToNull(all.getProperty(CLIENT_ID));
		this.client = blankToNull(all.getProperty(CLIENT));
		this.scope = all.getProperty(SCOPE, "openid");
		this.token = blankToNull(all.getProperty(TOKEN));
		this.user = blankToNull(all.getProperty(USER));
		this.password = all.getProperty(PASSWORD);
		this.cache = parseEnum(Cache.class, all.getProperty(TOKEN_CACHE, "memory"), TOKEN_CACHE);
		String file = blankToNull(all.getProperty(TOKEN_CACHE_FILE));
		this.cacheFile = file != null ? Path.of(file)
		                              : Path.of(System.getProperty("user.home"), ".acl-jdbc", "tokens.json");
		this.loginTimeoutSeconds = parseInt(all.getProperty(LOGIN_TIMEOUT, "300"), LOGIN_TIMEOUT);
		this.redirectPort = parseInt(all.getProperty(REDIRECT_PORT, "0"), REDIRECT_PORT);
		this.discovery = parseBool(all.getProperty(DISCOVERY, "true"));
		this.useEncryption = parseBool(all.getProperty(USE_ENCRYPTION, "true"));
		this.disableCertificateVerification = parseBool(all.getProperty(DISABLE_CERT_VERIFICATION, "false"));
		this.tlsRootCerts = blankToNull(all.getProperty(TLS_ROOT_CERTS));
		this.passthrough = new Properties();
		for (String key : all.stringPropertyNames()) {
			if (!OWN.contains(key)) {
				passthrough.setProperty(key, all.getProperty(key));
			}
		}
		passthrough.setProperty(USE_ENCRYPTION, Boolean.toString(useEncryption));
	}

	static boolean accepts(String url) {
		return url != null && url.regionMatches(true, 0, PREFIX, 0, PREFIX.length());
	}

	static AclConfig parse(String url, Properties info) throws SQLException {
		if (!accepts(url)) {
			throw new SQLException("not a duckdb-acl URL (expected " + PREFIX + "host:port): " + url);
		}
		String rest = url.substring(PREFIX.length());
		String query = "";
		int q = rest.indexOf('?');
		if (q >= 0) {
			query = rest.substring(q + 1);
			rest = rest.substring(0, q);
		}
		if (rest.endsWith("/")) {
			rest = rest.substring(0, rest.length() - 1);
		}
		String host = rest;
		int port = 443;
		int colon = rest.lastIndexOf(':');
		if (colon >= 0 && !rest.endsWith("]")) {
			host = rest.substring(0, colon);
			port = parseInt(rest.substring(colon + 1), "port");
		}
		if (host.isEmpty()) {
			throw new SQLException("no host in " + url);
		}
		Properties all = new Properties();
		for (String pair : query.split("&")) {
			if (pair.isEmpty()) {
				continue;
			}
			int eq = pair.indexOf('=');
			String key = decode(eq < 0 ? pair : pair.substring(0, eq));
			String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
			all.setProperty(key, value);
		}
		if (info != null) {
			for (String key : info.stringPropertyNames()) {
				all.setProperty(key, info.getProperty(key));
			}
		}
		return new AclConfig(host, port, all);
	}

	/** The URL Arrow's driver connects with: the same door, its own scheme. */
	String delegateUrl() {
		return DELEGATE_PREFIX + host + ":" + port;
	}

	/** What Arrow's driver receives: every key this driver does not own, and the token. */
	Properties delegateProperties(String bearer) {
		Properties out = new Properties();
		out.putAll(passthrough);
		out.setProperty(TOKEN, bearer);
		return out;
	}

	/** The flow this connection runs, {@code AUTO} resolved. */
	Flow effectiveFlow(boolean browserAvailable) {
		if (flow != Flow.AUTO) {
			return flow;
		}
		if (token != null) {
			return Flow.TOKEN;
		}
		if (user != null && password != null && !password.isEmpty()) {
			return Flow.PASSWORD;
		}
		return browserAvailable ? Flow.AUTHCODE : Flow.DEVICE;
	}

	static List<String> ownKeys() {
		return List.of(FLOW, ISSUER, CLIENT_ID, SCOPE, TOKEN, TOKEN_CACHE, TOKEN_CACHE_FILE, LOGIN_TIMEOUT,
		    REDIRECT_PORT, DISCOVERY);
	}

	private static String decode(String s) {
		return URLDecoder.decode(s, StandardCharsets.UTF_8);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static int parseInt(String s, String key) throws SQLException {
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			throw new SQLException(key + " must be a number, not '" + s + "'");
		}
	}

	private static boolean parseBool(String s) {
		return s.equalsIgnoreCase("true") || s.equals("1") || s.equalsIgnoreCase("yes");
	}

	private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String key) throws SQLException {
		try {
			return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			StringBuilder allowed = new StringBuilder();
			for (E option : type.getEnumConstants()) {
				allowed.append(allowed.length() > 0 ? ", " : "").append(option.name().toLowerCase(Locale.ROOT));
			}
			throw new SQLException(key + " must be one of " + allowed + ", not '" + value + "'");
		}
	}
}
