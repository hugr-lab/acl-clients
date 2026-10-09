package io.github.hugrlab.acl.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Struct;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Spec 006 end to end: what a tool reads through {@link AclDatabaseMetaData} and the result sets,
 * against the dev node seeded with {@code ACL_METADATA=1 dev/node.sh} (duckdb-acl spec 115 on the node),
 * signed in as analyst1 - an ORDINARY role: the metadata SQL must pass the function gate for a principal
 * that administers nothing. Enabled by ACL_E2E_URL and ACL_E2E_METADATA; writes a metadata dump to
 * {@code target/metadata-dump.txt} for review.
 */
@EnabledIfEnvironmentVariable(named = "ACL_E2E_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "ACL_E2E_METADATA", matches = ".+")
class LiveMetadataE2ETest {
	static final String URL = System.getenv("ACL_E2E_URL");
	static final StringBuilder DUMP = new StringBuilder();

	@BeforeAll
	static void fresh() {
		TokenCache.clearMemory();
	}

	@AfterAll
	static void writeDump() throws IOException {
		Path out = Path.of("target", "metadata-dump.txt");
		Files.createDirectories(out.getParent());
		Files.writeString(out, DUMP.toString(), StandardCharsets.UTF_8);
	}

	private static Connection connect(String... kv) throws SQLException {
		Properties p = new Properties();
		p.setProperty("user", "analyst1");
		p.setProperty("password", "analyst1-pass");
		for (int i = 0; i < kv.length; i += 2) {
			p.setProperty(kv[i], kv[i + 1]);
		}
		AclDriver driver = new AclDriver(new org.apache.arrow.driver.jdbc.ArrowFlightJdbcDriver(),
		    new TokenProvider(new FlightDiscovery(), new TestInteraction()));
		return driver.connect(URL, p);
	}

	/** The rows as maps (label → value), dumped for review under the title. */
	private static List<Map<String, Object>> rows(String title, ResultSet rs) throws SQLException {
		List<Map<String, Object>> out = new ArrayList<>();
		ResultSetMetaData meta = rs.getMetaData();
		DUMP.append("== ").append(title).append(" (").append(meta.getColumnCount()).append(" columns)\n");
		while (rs.next()) {
			Map<String, Object> row = new LinkedHashMap<>();
			for (int i = 1; i <= meta.getColumnCount(); i++) {
				row.put(meta.getColumnLabel(i), rs.getObject(i));
			}
			out.add(row);
			DUMP.append("  ").append(row).append('\n');
		}
		rs.close();
		return out;
	}

	private static Map<String, Object> only(List<Map<String, Object>> rows, String key, Object value) {
		for (Map<String, Object> row : rows) {
			if (value.equals(row.get(key))) {
				return row;
			}
		}
		throw new AssertionError("no row with " + key + " = " + value + " in " + rows);
	}

	@Test
	void sqlInfoFromTheNode() throws SQLException {
		try (Connection conn = connect()) {
			DatabaseMetaData meta = conn.getMetaData();
			assertEquals("\"", meta.getIdentifierQuoteString());
			assertTrue(meta.isCatalogAtStart());
			assertEquals("catalog", meta.getCatalogTerm());
			assertEquals("schema", meta.getSchemaTerm());
			assertTrue(meta.supportsTransactions());
			assertEquals("\\", meta.getSearchStringEscape());
			assertEquals(Connection.TRANSACTION_REPEATABLE_READ, meta.getDefaultTransactionIsolation());
			DUMP.append("== sqlinfo\n  product=").append(meta.getDatabaseProductName()).append(' ')
			    .append(meta.getDatabaseProductVersion()).append(", quote=").append(meta.getIdentifierQuoteString())
			    .append(", terms=").append(meta.getCatalogTerm()).append('/').append(meta.getSchemaTerm()).append('/')
			    .append(meta.getProcedureTerm()).append(", keywords=").append(meta.getSQLKeywords()).append('\n');
		}
	}

	@Test
	void theTree() throws SQLException {
		try (Connection conn = connect()) {
			DatabaseMetaData meta = conn.getMetaData();
			List<Map<String, Object>> catalogs = rows("getCatalogs", meta.getCatalogs());
			assertTrue(catalogs.stream().anyMatch(r -> "sales".equals(r.get("TABLE_CAT"))), catalogs.toString());
			assertTrue(catalogs.stream().anyMatch(r -> "inventory".equals(r.get("TABLE_CAT"))), catalogs.toString());

			List<Map<String, Object>> schemas = rows("getSchemas(sales)", meta.getSchemas("sales", null));
			List<Object> names = schemas.stream().map(r -> r.get("TABLE_SCHEM")).toList();
			assertTrue(names.containsAll(List.of("main", "raw", "raw.eu")), names.toString());
			assertTrue(schemas.stream().allMatch(r -> "sales".equals(r.get("TABLE_CATALOG"))), "only that catalog's");

			List<Map<String, Object>> tables = rows("getTables(sales.main)", meta.getTables("sales", "main", "%", null));
			Map<String, Object> customers = only(tables, "TABLE_NAME", "customers");
			assertEquals("BASE TABLE", customers.get("TABLE_TYPE"));
			assertEquals("customers with nested types", customers.get("REMARKS"));
			assertEquals("VIEW", only(tables, "TABLE_NAME", "big_orders").get("TABLE_TYPE"));
			rows("getTables(sales.raw.eu)", meta.getTables("sales", "raw.eu", "%", null));
			rows("getTables(inventory)", meta.getTables("inventory", null, "%", null));
		}
	}

	@Test
	void nestedColumns() throws SQLException {
		try (Connection conn = connect()) {
			List<Map<String, Object>> cols =
			    rows("getColumns(sales.main.customers)", conn.getMetaData().getColumns("sales", "main", "customers", null));
			Map<String, Object> address = only(cols, "COLUMN_NAME", "address");
			assertEquals("STRUCT(city VARCHAR, zip VARCHAR, geo STRUCT(lat DOUBLE, lon DOUBLE))", address.get("TYPE_NAME"));
			assertEquals(Types.STRUCT, address.get("DATA_TYPE"));
			assertEquals("VARCHAR[]", only(cols, "COLUMN_NAME", "tags").get("TYPE_NAME"));
			assertEquals(Types.ARRAY, only(cols, "COLUMN_NAME", "tags").get("DATA_TYPE"));
			assertEquals(Types.ARRAY, only(cols, "COLUMN_NAME", "scores").get("DATA_TYPE"));
			assertEquals(Types.OTHER, only(cols, "COLUMN_NAME", "attrs").get("DATA_TYPE"));
			Map<String, Object> balance = only(cols, "COLUMN_NAME", "balance");
			assertEquals(Types.DECIMAL, balance.get("DATA_TYPE"));
			assertEquals(18, balance.get("COLUMN_SIZE"));
			assertEquals(3, balance.get("DECIMAL_DIGITS"));
			assertEquals(Types.VARCHAR, only(cols, "COLUMN_NAME", "mood").get("DATA_TYPE"));
			assertEquals(Types.TIMESTAMP_WITH_TIMEZONE, only(cols, "COLUMN_NAME", "seen").get("DATA_TYPE"));
			assertEquals(10, cols.size());
		}
	}

	@Test
	void typesAndFunctions() throws SQLException {
		try (Connection conn = connect()) {
			DatabaseMetaData meta = conn.getMetaData();
			List<Map<String, Object>> types = rows("getTypeInfo", meta.getTypeInfo());
			assertFalse(types.isEmpty());
			assertEquals(Types.INTEGER, only(types, "TYPE_NAME", "INTEGER").get("DATA_TYPE"));

			List<Map<String, Object>> functions = rows("getFunctions(sales.main)", meta.getFunctions("sales", "main", "%"));
			Map<String, Object> over = only(functions, "FUNCTION_NAME", "orders_over");
			assertEquals((short) DatabaseMetaData.functionReturnsTable, over.get("FUNCTION_TYPE"));
			assertEquals("orders_over(INTEGER)", over.get("SPECIFIC_NAME"));
			assertEquals("orders at or over a threshold", over.get("REMARKS"));
			assertEquals((short) DatabaseMetaData.functionNoTable,
			    only(functions, "FUNCTION_NAME", "shout").get("FUNCTION_TYPE"));
			rows("getProcedures(sales.main)", meta.getProcedures("sales", "main", "%"));

			List<Map<String, Object>> fc =
			    rows("getFunctionColumns(orders_over)", meta.getFunctionColumns("sales", "main", "orders\\_over", null));
			assertEquals(List.of("threshold", "id", "amount"), fc.stream().map(r -> r.get("COLUMN_NAME")).toList());
			assertEquals((short) DatabaseMetaData.functionColumnIn, fc.get(0).get("COLUMN_TYPE"));
			assertEquals((short) DatabaseMetaData.functionColumnResult, fc.get(1).get("COLUMN_TYPE"));
			assertTrue(fc.stream().allMatch(r -> "orders_over(INTEGER)".equals(r.get("SPECIFIC_NAME"))));
			rows("getProcedureColumns(shout)", meta.getProcedureColumns("sales", "main", "shout", null));
		}
	}

	@Test
	void keys() throws SQLException {
		try (Connection conn = connect()) {
			DatabaseMetaData meta = conn.getMetaData();
			ResultSet none = meta.getPrimaryKeys("sales", "main", "orders");
			assertEquals(6, none.getMetaData().getColumnCount(), "an empty answer keeps its columns");
			assertEquals(4, none.findColumn("COLUMN_NAME"));
			assertTrue(rows("getPrimaryKeys(orders)", none).isEmpty());
			List<Map<String, Object>> pk = rows("getPrimaryKeys(customers)", meta.getPrimaryKeys("sales", "main",
			    "customers"));
			assertEquals("id", only(pk, "COLUMN_NAME", "id").get("COLUMN_NAME"));
			List<Map<String, Object>> imported = rows("getImportedKeys(orders)", meta.getImportedKeys("sales", "main",
			    "orders"));
			assertEquals("customers", only(imported, "FK_NAME", "order_customer").get("PKTABLE_NAME"));
			rows("getExportedKeys(customers)", meta.getExportedKeys("sales", "main", "customers"));
		}
	}

	@Test
	void resultTypesAndStructs() throws SQLException {
		try (Connection conn = connect(); Statement s = conn.createStatement();
		     ResultSet rs = s.executeQuery("SELECT id, address, tags, attrs, balance, ssn FROM customers")) {
			ResultSetMetaData meta = rs.getMetaData();
			assertEquals("STRUCT(city VARCHAR, zip VARCHAR, geo STRUCT(lat DOUBLE, lon DOUBLE))",
			    meta.getColumnTypeName(2));
			assertEquals(Types.STRUCT, meta.getColumnType(2));
			assertEquals("VARCHAR[]", meta.getColumnTypeName(3));
			assertEquals("MAP(VARCHAR, INTEGER)", meta.getColumnTypeName(4));
			assertEquals("DECIMAL(18,3)", meta.getColumnTypeName(5));
			assertEquals(Integer.class.getName(), meta.getColumnClassName(1));
			assertEquals(Struct.class.getName(), meta.getColumnClassName(2));
			assertTrue(rs.next());
			Struct address = assertInstanceOf(Struct.class, rs.getObject(2));
			assertEquals("Berlin", address.getAttributes()[0]);
			assertInstanceOf(Struct.class, address.getAttributes()[2]);
			assertInstanceOf(Array.class, rs.getObject(3));
			assertInstanceOf(Map.class, rs.getObject(4));
			assertEquals("***", rs.getString(6), "the grant's mask");
			DUMP.append("== result types\n");
			for (int i = 1; i <= meta.getColumnCount(); i++) {
				DUMP.append("  ").append(meta.getColumnLabel(i)).append(": ").append(meta.getColumnTypeName(i))
				    .append(" / ").append(meta.getColumnType(i)).append(" / ").append(meta.getColumnClassName(i))
				    .append(" = ").append(rs.getObject(i)).append('\n');
			}
		}
		try (Connection conn = connect("nested", "json"); Statement s = conn.createStatement();
		     ResultSet rs = s.executeQuery("SELECT address FROM customers")) {
			assertTrue(rs.next());
			assertEquals("{\"city\":\"Berlin\",\"zip\":\"10115\",\"geo\":{\"lat\":52.5,\"lon\":13.4}}", rs.getObject(1));
		}
	}

	@Test
	void catalogBySetCatalogAndUrl() throws SQLException {
		try (Connection conn = connect()) {
			assertEquals("sales", conn.getCatalog());
			conn.setCatalog("inventory");
			assertEquals("inventory", conn.getCatalog());
			try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT sku FROM products")) {
				assertTrue(rs.next());
				assertEquals("p-1", rs.getString(1));
			}
			conn.setCatalog("sales");
			conn.setSchema("raw.eu");
			assertEquals("raw.eu", conn.getSchema());
			try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT kind FROM events")) {
				assertTrue(rs.next());
			}
		}
		try (Connection conn = connect("catalog", "inventory")) {
			assertEquals("inventory", conn.getCatalog());
		}
	}

	@Test
	void modes() throws SQLException {
		String management = "CREATE VIRTUAL CATALOG e2e_probe";
		try (Connection conn = connect("acl.mode", "manage"); Statement s = conn.createStatement()) {
			SQLException e = assertThrows(SQLException.class, () -> s.execute(management));
			// the node read it as a management statement and judged the scope: the prefix went out
			assertTrue(e.getMessage().contains("no ACL administration scope"), e.getMessage());
			// metadata stays the virtual tree's in any mode
			assertNotNull(rows("getTables(manage mode)", conn.getMetaData().getTables("sales", "main", "orders", null)));
		}
		try (Connection conn = connect(); Statement s = conn.createStatement()) {
			SQLException e = assertThrows(SQLException.class, () -> s.execute(management));
			assertFalse(e.getMessage().contains("administration scope"), "data mode sends it as written: " + e.getMessage());
		}
		try (Connection conn = connect("acl.mode", "native"); Statement s = conn.createStatement()) {
			SQLException e = assertThrows(SQLException.class, () -> s.executeQuery("SELECT 1"));
			// sent as ACL NATIVE: the node's refusal of native SQL to a principal without passthrough
			assertTrue(e.getMessage().contains("ACL NATIVE SELECT 1") && e.getMessage().contains("administration scope"),
			    e.getMessage());
		}
	}
}
