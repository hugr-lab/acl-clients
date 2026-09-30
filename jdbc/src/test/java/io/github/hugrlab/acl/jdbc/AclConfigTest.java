package io.github.hugrlab.acl.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class AclConfigTest {
	@Test
	void urlAndPropertiesMerge() throws SQLException {
		Properties info = new Properties();
		info.setProperty("flow", "device");
		info.setProperty("disableCertificateVerification", "true");
		AclConfig c = AclConfig.parse("jdbc:acl://door.corp:32800/?flow=password&issuer=https%3A%2F%2Fidp%2Frealms%2Fx", info);
		assertEquals("door.corp", c.host);
		assertEquals(32800, c.port);
		assertEquals(AclConfig.Flow.DEVICE, c.flow, "a property wins over the URL");
		assertEquals("https://idp/realms/x", c.issuer);
		assertTrue(c.disableCertificateVerification);
		assertEquals("jdbc:arrow-flight-sql://door.corp:32800", c.delegateUrl());
	}

	@Test
	void delegateGetsTheTokenAndNothingOfOurs() throws SQLException {
		Properties info = new Properties();
		info.setProperty("user", "alice");
		info.setProperty("password", "secret");
		info.setProperty("tokenCache", "none");
		info.setProperty("trustStore", "/etc/ts.jks");
		AclConfig c = AclConfig.parse("jdbc:acl://door:1?clientId=app", info);
		Properties out = c.delegateProperties("at-1");
		assertEquals("at-1", out.getProperty("token"));
		assertEquals("/etc/ts.jks", out.getProperty("trustStore"), "a Flight driver key passes through");
		assertEquals("true", out.getProperty("useEncryption"), "TLS by default");
		assertNull(out.getProperty("user"), "the user never reaches the Flight driver: it would try BasicAuth");
		assertNull(out.getProperty("password"), "nor the password");
		assertNull(out.getProperty("clientId"));
		assertNull(out.getProperty("tokenCache"));
	}

	@Test
	void autoPicksFromWhatTheConnectionSupplies() throws SQLException {
		Properties token = new Properties();
		token.setProperty("token", "t");
		assertEquals(AclConfig.Flow.TOKEN, AclConfig.parse("jdbc:acl://d:1", token).effectiveFlow(true));
		Properties password = new Properties();
		password.setProperty("user", "u");
		password.setProperty("password", "p");
		assertEquals(AclConfig.Flow.PASSWORD, AclConfig.parse("jdbc:acl://d:1", password).effectiveFlow(true));
		Properties userOnly = new Properties();
		userOnly.setProperty("user", "u");
		userOnly.setProperty("password", "");
		assertEquals(AclConfig.Flow.AUTHCODE, AclConfig.parse("jdbc:acl://d:1", userOnly).effectiveFlow(true),
		    "an empty password is DBeaver's empty field, not a password");
		assertEquals(AclConfig.Flow.DEVICE, AclConfig.parse("jdbc:acl://d:1", null).effectiveFlow(false),
		    "no browser: the device code");
	}

	@Test
	void refusesWhatItCannotRead() {
		assertFalse(AclConfig.accepts("jdbc:arrow-flight-sql://d:1"));
		assertTrue(AclConfig.accepts("JDBC:ACL://d:1"));
		SQLException e = assertThrows(SQLException.class, () -> AclConfig.parse("jdbc:acl://d:1?flow=magic", null));
		assertTrue(e.getMessage().contains("authcode"), e.getMessage());
		assertThrows(SQLException.class, () -> AclConfig.parse("jdbc:acl://d:port", null));
		assertThrows(SQLException.class, () -> AclConfig.parse("jdbc:acl://:1", null));
	}
}
