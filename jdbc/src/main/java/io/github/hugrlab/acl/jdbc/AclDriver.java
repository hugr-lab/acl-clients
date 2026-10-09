package io.github.hugrlab.acl.jdbc;

import org.apache.arrow.driver.jdbc.ArrowFlightJdbcDriver;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * The duckdb-acl JDBC driver: {@code jdbc:acl://host:port}. It signs the user in - in a browser
 * (authorization code + PKCE), with a device code, with a user name and password, or with a token -
 * and connects through Apache Arrow's Flight SQL driver with the token it obtained. Tokens are kept
 * and refreshed between connections; the door checks the token when a connection is established.
 */
public final class AclDriver implements Driver {
	static final int MAJOR = 0;
	static final int MINOR = 1;

	static {
		try {
			DriverManager.registerDriver(new AclDriver());
		} catch (SQLException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private final Driver delegate;
	private final TokenProvider tokens;

	public AclDriver() {
		this(new ArrowFlightJdbcDriver(), new TokenProvider(new FlightDiscovery(), Interaction.desktop()));
	}

	AclDriver(Driver delegate, TokenProvider tokens) {
		this.delegate = delegate;
		this.tokens = tokens;
	}

	@Override
	public Connection connect(String url, Properties info) throws SQLException {
		if (!acceptsURL(url)) {
			return null; // JDBC: another driver's URL
		}
		AclConfig config = AclConfig.parse(url, info);
		String bearer = tokens.acquire(config);
		Connection arrow = delegate.connect(config.delegateUrl(), config.delegateProperties(bearer));
		return arrow == null ? null : new AclConnection(arrow, config);
	}

	@Override
	public boolean acceptsURL(String url) {
		return AclConfig.accepts(url);
	}

	@Override
	public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
		List<DriverPropertyInfo> out = new ArrayList<>();
		out.add(property(AclConfig.FLOW, "auto", "How to sign in: auto (token, else user+password, else browser, else "
		                                             + "device code), authcode, device, password, token",
		    "auto", "authcode", "device", "password", "token"));
		out.add(property(AclConfig.ISSUER, null,
		    "The OIDC issuer to sign in with, by URL or by the door's name for it; default: the first the door names "
		        + "with a client for the flow"));
		out.add(property(AclConfig.CLIENT, null,
		    "The door's client to sign in as, by name; default: the issuer's first client that runs the flow"));
		out.add(property(AclConfig.CLIENT_ID, null,
		    "A public OIDC client id to use instead of the door's clients (no discovery needed)"));
		out.add(property(AclConfig.SCOPE, "openid", "The scopes requested"));
		out.add(property(AclConfig.TOKEN, null, "A bearer token (flow=token): used as is, never refreshed"));
		out.add(property(AclConfig.TOKEN_CACHE, "memory",
		    "Where tokens are kept between connections: memory, file (survives restarts), none", "memory", "file",
		    "none"));
		out.add(property(AclConfig.TOKEN_CACHE_FILE, null, "The file cache's path; default ~/.acl-jdbc/tokens.json"));
		out.add(property(AclConfig.LOGIN_TIMEOUT, "300", "Seconds to wait for a browser or device sign-in"));
		out.add(property(AclConfig.REDIRECT_PORT, "0", "Loopback port for the browser redirect; 0 = any free port"));
		out.add(property(AclConfig.DISCOVERY, "true", "Ask the door which issuer and client to use", "true", "false"));
		out.add(property(AclConfig.LINEAGE_FROM_ENV, "true",
		    "Send OPENLINEAGE_PARENT_ID / OPENLINEAGE_ROOT_PARENT_ID as the lineage parent headers when the "
		        + "connection sets none",
		    "true", "false"));
		out.add(property(AclConfig.CATALOG, null,
		    "The catalog to start in (USE <catalog> once connected); default: the principal's main catalog"));
		out.add(property(AclConfig.SCHEMA, null, "The schema to start in (USE SCHEMA <schema> once connected)"));
		out.add(property(AclConfig.MODE, "data",
		    "What every statement is sent as: data (as written), manage (ACL <statement>), native (ACL NATIVE "
		        + "<statement>); the node decides by the principal's scope. Metadata is always the virtual catalog's",
		    "data", "manage", "native"));
		out.add(property(AclConfig.NESTED, "object",
		    "STRUCT / MAP / LIST values from getObject: object (java.sql.Struct, Map, java.sql.Array) or json (text)",
		    "object", "json"));
		out.add(property(AclConfig.USE_ENCRYPTION, "true", "TLS to the door", "true", "false"));
		out.add(property(AclConfig.DISABLE_CERT_VERIFICATION, "false",
		    "Skip the door's certificate check (development only)", "true", "false"));
		out.add(property(AclConfig.TLS_ROOT_CERTS, null, "PEM file with the CA that signed the door's certificate"));
		return out.toArray(new DriverPropertyInfo[0]);
	}

	private static DriverPropertyInfo property(String name, String value, String description, String... choices) {
		DriverPropertyInfo p = new DriverPropertyInfo(name, value);
		p.description = description;
		p.required = false;
		p.choices = choices.length == 0 ? null : choices;
		return p;
	}

	@Override
	public int getMajorVersion() {
		return MAJOR;
	}

	@Override
	public int getMinorVersion() {
		return MINOR;
	}

	@Override
	public boolean jdbcCompliant() {
		return false;
	}

	@Override
	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		throw new SQLFeatureNotSupportedException();
	}
}
