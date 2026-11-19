package io.github.hugrlab.acl.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hugrlab.acl.jdbc.AclIngest.Mode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.vector.IntVector;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.driver.jdbc.shaded.org.apache.arrow.vector.ipc.ArrowStreamWriter;
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

	/** The dev node's certificate is self-signed: without the development switch the driver says so (spec 003 addendum). */
	@Test
	void anUntrustedDoorIsNamedAsSuch() {
		String strict = URL.replace("disableCertificateVerification=true", "disableCertificateVerification=false");
		SQLException e = assertThrows(SQLException.class,
		    () -> driver(new TestInteraction()).connect(strict, props("user", "analyst2", "password", "analyst2-pass")));
		assertTrue(e.getMessage().contains("certificate this machine does not trust"), e.getMessage());
		assertTrue(e.getMessage().contains("tlsRootCerts"), e.getMessage());
		assertTrue(!e.getMessage().contains("set the issuer and clientId"), e.getMessage());
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

	/** An Arrow IPC stream of (sku, qty) rows: "sku=qty" each. */
	private static ByteArrayInputStream skus(String... rows) throws Exception {
		try (RootAllocator a = new RootAllocator(); VarCharVector sku = new VarCharVector("sku", a);
		     IntVector qty = new IntVector("qty", a)) {
			sku.allocateNew(rows.length);
			qty.allocateNew(rows.length);
			for (int i = 0; i < rows.length; i++) {
				String[] kv = rows[i].split("=");
				sku.setSafe(i, kv[0].getBytes(java.nio.charset.StandardCharsets.UTF_8));
				qty.set(i, Integer.parseInt(kv[1]));
			}
			sku.setValueCount(rows.length);
			qty.setValueCount(rows.length);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (VectorSchemaRoot root = VectorSchemaRoot.of(sku, qty);
			     ArrowStreamWriter w = new ArrowStreamWriter(root, null, out)) {
				w.start();
				w.writeBatch();
				w.end();
			}
			return new ByteArrayInputStream(out.toByteArray());
		}
	}

	private static List<String> stock(Connection conn, String run) throws SQLException {
		List<String> out = new ArrayList<>();
		try (Statement s = conn.createStatement();
		     ResultSet rs = s.executeQuery("SELECT sku, qty FROM stock WHERE sku LIKE '" + run + "%' ORDER BY sku")) {
			while (rs.next()) {
				out.add(rs.getString(1).substring(run.length()) + "=" + rs.getInt(2));
			}
		}
		return out;
	}

	/** spec 009: a load by key through the driver (duckdb-acl spec 173) - the last row of a key wins. */
	@Test
	void aLoadByKey() throws Exception {
		String run = UUID.randomUUID().toString().substring(0, 8) + "-";
		try (Connection conn = driver(new TestInteraction()).connect(URL, props("user", "analyst2", "password", "analyst2-pass"))) {
			AclIngest ingest = conn.unwrap(AclIngest.class);
			ingest.ingest("stock", skus(run + "a=1", run + "b=2", run + "a=3"), Mode.MERGE);
			assertEquals(List.of("a=3", "b=2"), stock(conn, run));
			ingest.ingest("stock", skus(run + "b=5", run + "c=1"), Mode.MERGE);
			assertEquals(List.of("a=3", "b=5", "c=1"), stock(conn, run));
			ingest.ingest("sales", null, "stock", skus(run + "a=0"), Mode.REMOVE);
			assertEquals(List.of("b=5", "c=1"), stock(conn, run));
			assertEquals(1, ingest.ingest("stock", skus(run + "d=4"), Mode.APPEND));
			assertEquals(List.of("b=5", "c=1", "d=4"), stock(conn, run));
			// orders: no ingest granted, no key declared - refused, nothing written
			SQLException e = assertThrows(SQLException.class, () -> ingest.ingest("orders", skus(run + "x=1"), Mode.MERGE));
			assertTrue(String.valueOf(e.getMessage()).contains("acl"), e.getMessage());
		}
	}
}
