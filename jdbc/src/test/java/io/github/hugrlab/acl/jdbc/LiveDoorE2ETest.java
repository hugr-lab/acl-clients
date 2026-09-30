package io.github.hugrlab.acl.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * End to end against a live duckdb-acl node and its Keycloak - the repo's dev/ setup
 * ({@code dev/keycloak.sh}, {@code dev/node.sh}). Enabled by ACL_E2E_URL (e.g.
 * {@code jdbc:acl://localhost:32800?disableCertificateVerification=true}); skipped otherwise. The
 * node's policy gives analyst1 tenant acme's rows and analyst2 globex's.
 */
@EnabledIfEnvironmentVariable(named = "ACL_E2E_URL", matches = ".+")
class LiveDoorE2ETest {
	static final String URL = System.getenv("ACL_E2E_URL");

	@BeforeEach
	void fresh() {
		TokenCache.clearMemory();
	}

	private static List<String> tenants(Connection conn) throws SQLException {
		List<String> out = new ArrayList<>();
		try (Statement s = conn.createStatement();
		     ResultSet rs = s.executeQuery("SELECT DISTINCT tenant FROM orders ORDER BY tenant")) {
			while (rs.next()) {
				out.add(rs.getString(1));
			}
		}
		return out;
	}

	private static Properties props(String... kv) {
		Properties p = new Properties();
		for (int i = 0; i < kv.length; i += 2) {
			p.setProperty(kv[i], kv[i + 1]);
		}
		return p;
	}

	private static AclDriver driver(Interaction interaction) {
		return new AclDriver(new org.apache.arrow.driver.jdbc.ArrowFlightJdbcDriver(),
		    new TokenProvider(new FlightDiscovery(), interaction));
	}

	@Test
	void passwordThroughTheDoorsDiscovery() throws SQLException {
		try (Connection conn = driver(new TestInteraction()).connect(URL, props("user", "analyst2", "password", "analyst2-pass"))) {
			assertEquals(List.of("globex"), tenants(conn));
		}
	}

	@Test
	void aWrongPasswordIsTheIdpsRefusal() {
		SQLException e = assertThrows(SQLException.class,
		    () -> driver(new TestInteraction()).connect(URL, props("user", "analyst2", "password", "nope")));
		assertEquals("28000", e.getSQLState());
		assertTrue(e.getMessage().contains("refused the password sign-in"), e.getMessage());
	}

	@Test
	void browserSignIn() throws SQLException {
		KeycloakBrowser person = new KeycloakBrowser("analyst1", "analyst1-pass");
		try (Connection conn = driver(person).connect(URL, props("flow", "authcode", "loginTimeout", "30"))) {
			assertEquals(List.of("acme"), tenants(conn));
		}
		assertTrue(person.pagesSubmitted >= 1, "the login form was filled");
	}

	@Test
	void deviceSignIn() throws SQLException {
		KeycloakBrowser person = new KeycloakBrowser("analyst2", "analyst2-pass");
		try (Connection conn = driver(person).connect(URL, props("flow", "device", "loginTimeout", "60"))) {
			assertEquals(List.of("globex"), tenants(conn));
		}
	}

	@Test
	void theSecondConnectionReusesTheSignIn() throws SQLException {
		KeycloakBrowser person = new KeycloakBrowser("analyst1", "analyst1-pass");
		AclDriver driver = driver(person);
		try (Connection a = driver.connect(URL, props("flow", "authcode"));
		     Connection b = driver.connect(URL, props("flow", "authcode"))) {
			assertEquals(tenants(a), tenants(b));
		}
		int afterTwo = person.pagesSubmitted;
		assertTrue(afterTwo >= 1 && afterTwo <= 2, "one sign-in, not two: " + afterTwo);
	}
}
