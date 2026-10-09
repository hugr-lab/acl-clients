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

	// spec 004: the orchestrator's parent becomes the call headers the node reads
	@Test
	void lineageParentFromTheEnvironment() throws SQLException {
		java.util.Map<String, String> env = java.util.Map.of(AclConfig.PARENT_ENV, " airflow/daily.load/01929e3a-0000-7000-8000-000000000001 ",
		    AclConfig.ROOT_PARENT_ENV, "airflow/daily/01929e3a-0000-7000-8000-0000000000ff");
		Properties out = AclConfig.parse("jdbc:acl://door:1", new Properties(), env::get).delegateProperties("t");
		assertEquals("airflow/daily.load/01929e3a-0000-7000-8000-000000000001", out.getProperty(AclConfig.PARENT_HEADER));
		assertEquals("airflow/daily/01929e3a-0000-7000-8000-0000000000ff", out.getProperty(AclConfig.ROOT_PARENT_HEADER));
		assertNull(out.getProperty(AclConfig.LINEAGE_FROM_ENV), "our key is never passed on");
	}

	@Test
	void theConnectionsOwnParentWins() throws SQLException {
		Properties info = new Properties();
		info.setProperty(AclConfig.PARENT_HEADER, "mine/job/01929e3a-0000-7000-8000-000000000009");
		java.util.Map<String, String> env = java.util.Map.of(AclConfig.PARENT_ENV, "airflow/daily.load/01929e3a-0000-7000-8000-000000000001");
		Properties out = AclConfig.parse("jdbc:acl://door:1", info, env::get).delegateProperties("t");
		assertEquals("mine/job/01929e3a-0000-7000-8000-000000000009", out.getProperty(AclConfig.PARENT_HEADER));
	}

	@Test
	void noEnvironmentNoHeader() throws SQLException {
		Properties out = AclConfig.parse("jdbc:acl://door:1", new Properties(), k -> null).delegateProperties("t");
		assertNull(out.getProperty(AclConfig.PARENT_HEADER));
		assertNull(out.getProperty(AclConfig.ROOT_PARENT_HEADER));
	}

	@Test
	void theEnvironmentCanBeTurnedOff() throws SQLException {
		Properties info = new Properties();
		info.setProperty(AclConfig.LINEAGE_FROM_ENV, "false");
		java.util.Map<String, String> env = java.util.Map.of(AclConfig.PARENT_ENV, "airflow/daily.load/01929e3a-0000-7000-8000-000000000001");
		Properties out = AclConfig.parse("jdbc:acl://door:1", info, env::get).delegateProperties("t");
		assertNull(out.getProperty(AclConfig.PARENT_HEADER));
	}

	@Test
	void aDifferentlyCasedOwnHeaderStillWins() throws SQLException {
		Properties info = new Properties();
		info.setProperty("X-OpenLineage-Parent", "mine/job/01929e3a-0000-7000-8000-000000000009");
		java.util.Map<String, String> env = java.util.Map.of(AclConfig.PARENT_ENV, "airflow/daily.load/01929e3a-0000-7000-8000-000000000001");
		Properties out = AclConfig.parse("jdbc:acl://door:1", info, env::get).delegateProperties("t");
		assertNull(out.getProperty(AclConfig.PARENT_HEADER), "no second header beside the connection's own");
		assertEquals("mine/job/01929e3a-0000-7000-8000-000000000009", out.getProperty("X-OpenLineage-Parent"));
	}

	@Test
	void aMalformedEnvironmentValueIsNotSent() throws SQLException {
		for (String bad : new String[] {"airflow/daily/not-a-uuid", "no-slashes", "a/b/01929e3a-0000-7000-8000-000000000001\nx: y"}) {
			java.util.Map<String, String> env = java.util.Map.of(AclConfig.PARENT_ENV, bad);
			Properties out = AclConfig.parse("jdbc:acl://door:1", new Properties(), env::get).delegateProperties("t");
			assertNull(out.getProperty(AclConfig.PARENT_HEADER), bad);
		}
	}

	@Test
	void aNamespaceWithASlashIsAccepted() throws SQLException {
		java.util.Map<String, String> env = java.util.Map.of(AclConfig.ROOT_PARENT_ENV, "airflow://prod/daily/01929e3a-0000-7000-8000-0000000000ff");
		Properties out = AclConfig.parse("jdbc:acl://door:1", new Properties(), env::get).delegateProperties("t");
		assertEquals("airflow://prod/daily/01929e3a-0000-7000-8000-0000000000ff", out.getProperty(AclConfig.ROOT_PARENT_HEADER));
		assertNull(out.getProperty(AclConfig.PARENT_HEADER), "a root without a parent is the root alone");
	}

	// the load-bearing assumption: Arrow's driver sends a property it does not own as a call header
	@Test
	void arrowSendsTheParentAsAHeader() throws SQLException {
		java.util.Map<String, String> env = java.util.Map.of(AclConfig.PARENT_ENV, "airflow/daily.load/01929e3a-0000-7000-8000-000000000001");
		Properties out = AclConfig.parse("jdbc:acl://door:1", new Properties(), env::get).delegateProperties("t");
		org.apache.arrow.driver.jdbc.utils.ArrowFlightConnectionConfigImpl arrow =
		    new org.apache.arrow.driver.jdbc.utils.ArrowFlightConnectionConfigImpl(out);
		assertEquals("airflow/daily.load/01929e3a-0000-7000-8000-000000000001",
		    arrow.getHeaderAttributes().get(AclConfig.PARENT_HEADER));
	}
}
