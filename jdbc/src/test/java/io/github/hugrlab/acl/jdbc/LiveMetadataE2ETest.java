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
			List<Map<String, Object>> eu = rows("getTables(sales.raw.eu)", meta.getTables("sales", "raw.eu", "%", null));
			Map<String, Object> events = only(eu, "TABLE_NAME", "events");
			assertEquals("raw.eu", events.get("TABLE_SCHEM"));
			assertEquals("BASE TABLE", events.get("TABLE_TYPE"));
			List<Map<String, Object>> inventory = rows("getTables(inventory)", meta.getTables("inventory", null, "%", null));
			Map<String, Object> products = only(inventory, "TABLE_NAME", "products");
			assertEquals("inventory", products.get("TABLE_CAT"));
			assertEquals("the price list", products.get("REMARKS"));
			// a nested schema by its path, and only that one
			List<Map<String, Object>> rawEu = rows("getSchemas(sales, raw.eu)", meta.getSchemas("sales", "raw.eu"));
			assertEquals(List.of("raw.eu"), rawEu.stream().map(r -> r.get("TABLE_SCHEM")).toList());
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
			assertEquals("INTEGER[3]", only(cols, "COLUMN_NAME", "scores").get("TYPE_NAME"));
			assertEquals("STRUCT(city VARCHAR, n INTEGER)[]", only(cols, "COLUMN_NAME", "visits").get("TYPE_NAME"));
			assertEquals(Types.OTHER, only(cols, "COLUMN_NAME", "attrs").get("DATA_TYPE"));
			Map<String, Object> balance = only(cols, "COLUMN_NAME", "balance");
			assertEquals(Types.DECIMAL, balance.get("DATA_TYPE"));
			assertEquals(18, balance.get("COLUMN_SIZE"));
			assertEquals(3, balance.get("DECIMAL_DIGITS"));
			assertEquals(Types.VARCHAR, only(cols, "COLUMN_NAME", "mood").get("DATA_TYPE"));
			assertEquals(Types.TIMESTAMP_WITH_TIMEZONE, only(cols, "COLUMN_NAME", "seen").get("DATA_TYPE"));
			assertEquals(11, cols.size());
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
			Map<String, Object> tenants = only(functions, "FUNCTION_NAME", "all_tenants");
			assertEquals((short) DatabaseMetaData.functionReturnsTable, tenants.get("FUNCTION_TYPE"));
			assertEquals("all_tenants()", tenants.get("SPECIFIC_NAME"));
			assertEquals("every tenant", tenants.get("REMARKS"));
			rows("getProcedures(sales.main)", meta.getProcedures("sales", "main", "%"));

			List<Map<String, Object>> fc =
			    rows("getFunctionColumns(orders_over)", meta.getFunctionColumns("sales", "main", "orders\\_over", null));
			assertEquals(List.of("threshold", "id", "amount"), fc.stream().map(r -> r.get("COLUMN_NAME")).toList());
			assertEquals((short) DatabaseMetaData.functionColumnIn, fc.get(0).get("COLUMN_TYPE"));
			assertEquals((short) DatabaseMetaData.functionColumnResult, fc.get(1).get("COLUMN_TYPE"));
			assertTrue(fc.stream().allMatch(r -> "orders_over(INTEGER)".equals(r.get("SPECIFIC_NAME"))));
			rows("getProcedureColumns(shout)", meta.getProcedureColumns("sales", "main", "shout", null));
			// a scalar's return value, also when a column pattern is given
			for (String pattern : new String[] {null, "returnValue"}) {
				List<Map<String, Object>> ret = rows("getFunctionColumns(shout, " + pattern + ")",
				    meta.getFunctionColumns("sales", "main", "shout", pattern));
				Map<String, Object> value = only(ret, "COLUMN_NAME", AclDatabaseMetaData.RETURN_VALUE);
				assertEquals((short) DatabaseMetaData.functionReturn, value.get("COLUMN_TYPE"));
				assertEquals(0, value.get("ORDINAL_POSITION"));
				assertEquals("shout(VARCHAR)", value.get("SPECIFIC_NAME"));
				assertEquals("VARCHAR", value.get("TYPE_NAME"));
			}
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
			Map<String, Object> id = only(pk, "COLUMN_NAME", "id");
			assertEquals((short) 1, id.get("KEY_SEQ"));
			assertEquals("customers_pk", id.get("PK_NAME"));
			assertEquals("main", id.get("TABLE_SCHEM"));
			List<Map<String, Object>> imported = rows("getImportedKeys(orders)", meta.getImportedKeys("sales", "main",
			    "orders"));
			Map<String, Object> fk = only(imported, "FK_NAME", "order_customer");
			assertEquals("customers", fk.get("PKTABLE_NAME"));
			assertEquals("orders", fk.get("FKTABLE_NAME"));
			assertEquals((short) 1, fk.get("KEY_SEQ"));
			assertEquals("customers_pk", fk.get("PK_NAME"));
			List<Map<String, Object>> exported = rows("getExportedKeys(customers)", meta.getExportedKeys("sales", "main",
			    "customers"));
			assertEquals(1, exported.size(), exported.toString());
			Map<String, Object> out = exported.get(0);
			assertEquals("order_customer", out.get("FK_NAME"));
			assertEquals("orders", out.get("FKTABLE_NAME"));
			assertEquals("id", out.get("FKCOLUMN_NAME"));
			assertEquals("customers", out.get("PKTABLE_NAME"));
			assertEquals("id", out.get("PKCOLUMN_NAME"));
			assertEquals(fk, out, "the same reference from either end");
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
		try (Connection conn = connect(); Statement s = conn.createStatement();
		     ResultSet rs = s.executeQuery("SELECT visits FROM customers")) {
			// a STRUCT inside a LIST is a Struct too
			assertTrue(rs.next());
			Array visits = assertInstanceOf(Array.class, rs.getObject(1));
			Object[] items = (Object[]) visits.getArray();
			assertEquals(2, items.length);
			Struct berlin = assertInstanceOf(Struct.class, items[0]);
			assertEquals(List.of("Berlin", 2), List.of(berlin.getAttributes()));
			ResultSet itemRows = rs.getArray(1).getResultSet();
			assertTrue(itemRows.next());
			assertInstanceOf(Struct.class, itemRows.getObject("VALUE"));
			assertInstanceOf(Struct.class, ((Object[]) rs.getObject(1, Array.class).getArray())[1]);
		}
		try (Connection conn = connect();
		     java.sql.PreparedStatement p = conn.prepareStatement("SELECT address, balance, visits FROM customers")) {
			// a prepared statement's metadata, before it runs: the dataset schema of the prepare
			ResultSetMetaData meta = p.getMetaData();
			assertEquals("STRUCT(city VARCHAR, zip VARCHAR, geo STRUCT(lat DOUBLE, lon DOUBLE))",
			    meta.getColumnTypeName(1));
			assertEquals(Types.STRUCT, meta.getColumnType(1));
			assertEquals("DECIMAL(18,3)", meta.getColumnTypeName(2));
			assertEquals("STRUCT(city VARCHAR, n INTEGER)[]", meta.getColumnTypeName(3));
		}
		try (Connection conn = connect(); Statement s = conn.createStatement();
		     ResultSet rs = s.executeQuery("SELECT 123::BIGNUM AS b, -123::BIGNUM AS n, '101'::BIT AS t")) {
			// duckdb's own bytes on the wire, read as their text
			assertTrue(rs.next());
			assertEquals("123", rs.getString(1));
			assertEquals("-123", rs.getObject(2));
			assertEquals(new java.math.BigDecimal(-123), rs.getBigDecimal(2));
			assertEquals("101", rs.getString(3));
			assertEquals(String.class.getName(), rs.getMetaData().getColumnClassName(1));
			assertEquals(Types.OTHER, rs.getMetaData().getColumnType(3));
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
		try (Connection conn = connect("catalog", "sales", "schema", "raw.eu"); Statement s = conn.createStatement();
		     ResultSet rs = s.executeQuery("SELECT kind FROM events")) {
			assertEquals("sales", conn.getCatalog());
			assertEquals("raw.eu", conn.getSchema());
			assertTrue(rs.next());
			assertEquals("click", rs.getString(1));
		}
		// review 2026-10-09: a USE as the connection's first statement was lost (a cookie-less call)
		try (Connection conn = connect(); Statement s = conn.createStatement()) {
			s.execute("USE \"inventory\"");
			try (ResultSet rs = s.executeQuery("SELECT sku FROM products")) {
				assertTrue(rs.next());
				assertEquals("p-1", rs.getString(1));
			}
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
			// a leading comment is dropped for the management grammar, which reads none
			SQLException commented = assertThrows(SQLException.class, () -> s.execute("/* why */ " + management));
			assertTrue(commented.getMessage().contains("no ACL administration scope"), commented.getMessage());
			// metadata stays the virtual tree's in any mode
			Map<String, Object> orders = only(rows("getTables(manage mode)",
			    conn.getMetaData().getTables("sales", "main", "orders", null)), "TABLE_NAME", "orders");
			assertEquals("sales", orders.get("TABLE_CAT"));
			assertEquals("BASE TABLE", orders.get("TABLE_TYPE"));
			// the session's catalog is virtual in every mode: getCatalog unprefixed, a USE as written
			assertEquals("sales", conn.getCatalog());
			s.execute("USE \"inventory\"");
			assertEquals("inventory", conn.getCatalog());
			conn.setCatalog("sales");
			assertEquals("sales", conn.getCatalog());
		}
		try (Connection conn = connect(); Statement s = conn.createStatement()) {
			SQLException e = assertThrows(SQLException.class, () -> s.execute(management));
			// data mode sends it as written: duckdb's parser, not the management grammar, refuses it
			assertTrue(e.getMessage().contains("syntax error at or near \"VIRTUAL\""), e.getMessage());
			assertFalse(e.getMessage().contains("administration scope"), e.getMessage());
		}
		try (Connection conn = connect("acl.mode", "native"); Statement s = conn.createStatement()) {
			SQLException e = assertThrows(SQLException.class, () -> s.executeQuery("SELECT 1"));
			// sent as ACL NATIVE: the node's refusal of native SQL to a principal without passthrough
			assertTrue(e.getMessage().contains("ACL NATIVE SELECT 1") && e.getMessage().contains("administration scope"),
			    e.getMessage());
			// the comment stays in the text after the prefix (duckdb's parser reads it)
			SQLException commented = assertThrows(SQLException.class, () -> s.executeQuery("/* c */ SELECT 1"));
			assertTrue(commented.getMessage().contains("ACL NATIVE /* c */ SELECT 1"), commented.getMessage());
			// a USE is the session's, as written: no passthrough needed
			s.execute("USE \"inventory\"");
			assertEquals("inventory", conn.getCatalog());
		}
	}
}
