package io.github.hugrlab.acl.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hugrlab.acl.jdbc.AclConfig.Mode;
import io.github.hugrlab.acl.jdbc.FakeArrow.Answer;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AclConnectionTest {
	private static final String CURRENT = "SELECT current_database(), current_schema()";

	private static FakeArrow node(AtomicReference<String> catalog) {
		return new FakeArrow(sql -> {
			if (sql.equals(CURRENT)) {
				return new Answer(List.of("db", "schema"), List.<Object[]>of(new Object[] {catalog.get(), "main"}));
			}
			if (sql.toUpperCase(java.util.Locale.ROOT).startsWith("USE \"")) {
				catalog.set(sql.substring(5, sql.length() - 1));
			}
			return null;
		});
	}

	private static AclConfig config(String... kv) throws SQLException {
		Properties p = new Properties();
		for (int i = 0; i < kv.length; i += 2) {
			p.setProperty(kv[i], kv[i + 1]);
		}
		return AclConfig.parse("jdbc:acl://door:1", p, name -> null);
	}

	@Test
	void modePrefixesEveryStatementPath() throws SQLException {
		FakeArrow arrow = node(new AtomicReference<>("sales"));
		Connection conn = new AclConnection(arrow.connection(), Mode.MANAGE, false);
		try (Statement s = conn.createStatement()) {
			s.execute("CREATE VIRTUAL CATALOG c");
			s.executeQuery("SHOW GRANTS");
			s.addBatch("DROP VIRTUAL CATALOG c");
		}
		conn.prepareStatement("GRANT CATALOG c TO ROLE r").execute();
		conn.prepareCall("REVOKE CATALOG c FROM ROLE r");
		assertEquals("ACL CREATE CATALOG x", conn.nativeSQL("CREATE CATALOG x"));
		assertEquals(List.of("ACL CREATE VIRTUAL CATALOG c", "ACL SHOW GRANTS", "ACL DROP VIRTUAL CATALOG c",
		                 "ACL GRANT CATALOG c TO ROLE r"),
		    arrow.sent);
		assertEquals(List.of("ACL GRANT CATALOG c TO ROLE r", "ACL REVOKE CATALOG c FROM ROLE r"), arrow.prepared);

		FakeArrow plain = node(new AtomicReference<>("sales"));
		Connection data = new AclConnection(plain.connection(), Mode.DATA, false);
		data.createStatement().execute("SELECT 1");
		assertEquals(List.of("SELECT 1"), plain.sent);
		FakeArrow nativeArrow = node(new AtomicReference<>("sales"));
		new AclConnection(nativeArrow.connection(), Mode.NATIVE, false).createStatement().execute("SELECT 1");
		assertEquals(List.of("ACL NATIVE SELECT 1"), nativeArrow.sent);
	}

	@Test
	void catalogAndSchemaAreUse() throws SQLException {
		AtomicReference<String> catalog = new AtomicReference<>("sales");
		FakeArrow arrow = node(catalog);
		Connection conn = new AclConnection(arrow.connection(), Mode.MANAGE, false);
		conn.setCatalog("inventory");
		conn.setSchema("raw.eu");
		// a first call earns the session cookie before the USE (duckdb-acl spec 050); never prefixed
		assertEquals(List.of("SELECT 1", "USE \"inventory\"", "USE SCHEMA \"raw.eu\""), arrow.sent);
		assertEquals("inventory", conn.getCatalog());
		assertEquals("main", conn.getSchema());
		assertEquals(1, arrow.sent.stream().filter(CURRENT::equals).count(), "read once, then kept");
		conn.createStatement().execute("use \"sales\""); // through any statement of the connection
		assertEquals("sales", conn.getCatalog());
		assertEquals(2, arrow.sent.stream().filter(CURRENT::equals).count(), "a USE forgets what was kept");
	}

	@Test
	void urlCatalogAndSchemaAreAppliedAtConnect() throws SQLException {
		AtomicReference<String> catalog = new AtomicReference<>("sales");
		FakeArrow arrow = node(catalog);
		Connection conn = new AclConnection(arrow.connection(), config("catalog", "inventory", "schema", "s"));
		assertEquals(List.of("SELECT 1", "USE \"inventory\"", "USE SCHEMA \"s\""), arrow.sent);
		assertEquals("inventory", conn.getCatalog());
	}

	@Test
	void metadataIsNeverPrefixedAndEmptyAnswersHaveTheirColumns() throws SQLException {
		FakeArrow arrow = new FakeArrow(sql -> null); // every listing empty, with no columns at all
		Connection conn = new AclConnection(arrow.connection(), Mode.NATIVE, false);
		DatabaseMetaData meta = conn.getMetaData();
		assertSame(conn, meta.getConnection());
		ResultSet keys = meta.getPrimaryKeys("sales", "main", "orders");
		assertFalse(keys.next());
		ResultSetMetaData shape = keys.getMetaData();
		assertEquals(6, shape.getColumnCount());
		assertEquals("COLUMN_NAME", shape.getColumnLabel(4));
		assertEquals(4, keys.findColumn("column_name"));
		assertEquals(24, meta.getColumns("sales", "main", "orders", "%").getMetaData().getColumnCount());
		assertEquals(10, meta.getTables("sales", "main", "%", null).getMetaData().getColumnCount());
		assertEquals(14, meta.getImportedKeys("sales", "main", "orders").getMetaData().getColumnCount());
		assertEquals(14, meta.getExportedKeys("sales", "main", "orders").getMetaData().getColumnCount());
		assertEquals(6, meta.getFunctions("sales", "main", "%").getMetaData().getColumnCount());
		assertEquals(9, meta.getProcedures("sales", "main", "%").getMetaData().getColumnCount());
		assertEquals(17, meta.getFunctionColumns("sales", "main", "%", "%").getMetaData().getColumnCount());
		assertEquals(20, meta.getProcedureColumns("sales", "main", "%", "%").getMetaData().getColumnCount());
		assertEquals(18, meta.getTypeInfo().getMetaData().getColumnCount());
		assertEquals(2, meta.getSchemas("sales", null).getMetaData().getColumnCount());
		assertFalse(arrow.sent.isEmpty());
		for (String sql : arrow.sent) {
			assertFalse(sql.startsWith("ACL"), "metadata SQL is the virtual tree's, whatever acl.mode: " + sql);
			MetadataSqlTest.onlyListingsAreCalled(sql);
		}
	}

	@Test
	void listingsMapTypesInJava() throws SQLException {
		FakeArrow arrow = new FakeArrow(sql -> {
			if (sql.contains("duckdb_columns()")) {
				return new Answer(List.of("database_name", "schema_name", "table_name", "column_name", "column_index",
				                      "comment", "column_default", "is_nullable", "data_type", "is_generated"),
				    List.of(new Object[] {"sales", "main", "customers", "address", 3, "where", null, true,
				                "STRUCT(city VARCHAR)", false},
				        new Object[] {"sales", "main", "customers", "balance", 7, null, null, false, "DECIMAL(18,3)",
				            false}));
			}
			if (sql.contains("acl_function_columns()")) {
				return new Answer(List.of("database_name", "schema_name", "function_name", "function_type",
				                      "column_kind", "position", "column_name", "data_type", "is_nullable", "comment"),
				    List.of(new Object[] {"sales", "main", "orders_over", "table", "column", 1, "id", "INTEGER", null, null},
				        new Object[] {"sales", "main", "orders_over", "table", "param", 1, "threshold", "INTEGER", null,
				            null}));
			}
			if (sql.contains("duckdb_functions()")) {
				return new Answer(List.of("database_name", "schema_name", "function_name", "function_type", "comment",
				                      "description", "parameter_types"),
				    List.<Object[]>of(new Object[] {"sales", "main", "orders_over", "table", "big orders", null,
				        Arrays.asList("INTEGER")}));
			}
			if (sql.contains("acl_references()")) {
				return new Answer(List.of("vcat", "name", "from_object", "to_object", "from_column_list",
				                      "to_column_list"),
				    List.<Object[]>of(new Object[] {"sales", "order_customer", "orders", "raw.eu.customers",
				        List.of("customer_id", "region"), List.of("id", "region")}));
			}
			return null;
		});
		DatabaseMetaData meta = new AclConnection(arrow.connection(), Mode.DATA, false).getMetaData();

		ResultSet columns = meta.getColumns("sales", "main", "customers", null);
		assertTrue(columns.next());
		assertEquals("STRUCT(city VARCHAR)", columns.getString("TYPE_NAME"));
		assertEquals(Types.STRUCT, columns.getInt("DATA_TYPE"));
		assertEquals("where", columns.getString("REMARKS"));
		assertEquals("YES", columns.getString("IS_NULLABLE"));
		assertTrue(columns.next());
		assertEquals(Types.DECIMAL, columns.getInt("DATA_TYPE"));
		assertEquals(18, columns.getInt("COLUMN_SIZE"));
		assertEquals(3, columns.getInt("DECIMAL_DIGITS"));
		assertEquals(DatabaseMetaData.columnNoNulls, columns.getInt("NULLABLE"));

		ResultSet functions = meta.getFunctions("sales", "main", "orders%");
		assertTrue(functions.next());
		assertEquals("orders_over", functions.getString(3));
		assertEquals("big orders", functions.getString("REMARKS"));
		assertEquals(DatabaseMetaData.functionReturnsTable, functions.getShort("FUNCTION_TYPE"));
		assertEquals("orders_over(INTEGER)", functions.getString("SPECIFIC_NAME"));

		ResultSet fc = meta.getFunctionColumns("sales", "main", "orders_over", null);
		List<String> rows = new ArrayList<>();
		while (fc.next()) {
			rows.add(fc.getString("COLUMN_NAME") + ":" + fc.getShort("COLUMN_TYPE") + ":" + fc.getString("SPECIFIC_NAME"));
		}
		assertEquals(List.of("threshold:" + DatabaseMetaData.functionColumnIn + ":orders_over(INTEGER)",
		                 "id:" + DatabaseMetaData.functionColumnResult + ":orders_over(INTEGER)"),
		    rows);

		ResultSet imported = meta.getImportedKeys("sales", "main", "orders");
		assertTrue(imported.next());
		assertEquals("raw.eu", imported.getString("PKTABLE_SCHEM"));
		assertEquals("customers", imported.getString("PKTABLE_NAME"));
		assertEquals("id", imported.getString("PKCOLUMN_NAME"));
		assertEquals("customer_id", imported.getString("FKCOLUMN_NAME"));
		assertEquals(1, imported.getShort("KEY_SEQ"));
		assertTrue(imported.next());
		assertEquals(2, imported.getShort("KEY_SEQ"));
		assertFalse(imported.next());
	}

	@Test
	void unwrapReachesArrow() throws SQLException {
		FakeArrow arrow = new FakeArrow(sql -> null);
		Connection inner = arrow.connection();
		Connection conn = new AclConnection(inner, Mode.DATA, false);
		assertInstanceOf(AclConnection.class, conn.unwrap(AclConnection.class));
		assertTrue(conn.isWrapperFor(AclConnection.class));
		Statement s = conn.createStatement();
		assertSame(conn, s.getConnection());
	}
}
