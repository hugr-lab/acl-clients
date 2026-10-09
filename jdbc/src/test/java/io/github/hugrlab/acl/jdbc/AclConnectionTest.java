package io.github.hugrlab.acl.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hugrlab.acl.jdbc.AclConfig.Mode;
import io.github.hugrlab.acl.jdbc.FakeArrow.Answer;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
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
			s.executeBatch();
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

	// review 2026-10-09: a USE as the connection's first statement went out cookie-less and was lost
	@Test
	void aFirstUseEarnsTheCookieFirst() throws SQLException {
		FakeArrow arrow = node(new AtomicReference<>("sales"));
		Connection conn = new AclConnection(arrow.connection(), Mode.DATA, false);
		conn.createStatement().execute("USE \"inventory\"");
		assertEquals(List.of("SELECT 1", "USE \"inventory\""), arrow.sent);

		// a call that failed earned nothing: the next USE still warms up first
		FakeArrow refusing = node(new AtomicReference<>("sales"));
		refusing.refuses = sql -> sql.equals("SELECT broken");
		Connection second = new AclConnection(refusing.connection(), Mode.DATA, false);
		assertThrows(SQLException.class, () -> second.createStatement().execute("SELECT broken"));
		second.createStatement().execute("USE \"inventory\"");
		assertEquals(List.of("SELECT broken", "SELECT 1", "USE \"inventory\""), refusing.sent);

		// a prepared USE too, before its prepare
		FakeArrow prepared = node(new AtomicReference<>("sales"));
		new AclConnection(prepared.connection(), Mode.DATA, false).prepareStatement("USE \"inventory\"").execute();
		assertEquals(List.of("SELECT 1", "USE \"inventory\""), prepared.sent);

		// any other first statement is the warm-up itself
		FakeArrow plain = node(new AtomicReference<>("sales"));
		Connection third = new AclConnection(plain.connection(), Mode.DATA, false);
		third.createStatement().execute("SELECT 2");
		third.createStatement().execute("USE \"inventory\"");
		assertEquals(List.of("SELECT 2", "USE \"inventory\""), plain.sent);
	}

	// review 2026-10-09: the kept catalog was forgotten at addBatch, then read again before the batch ran
	@Test
	void aBatchWithUseForgetsTheCatalogWhenItHasRun() throws SQLException {
		AtomicReference<String> catalog = new AtomicReference<>("sales");
		FakeArrow arrow = node(catalog);
		Connection conn = new AclConnection(arrow.connection(), Mode.DATA, false);
		Statement s = conn.createStatement();
		s.addBatch("USE \"inventory\"");
		assertEquals("sales", conn.getCatalog(), "the batch has not run");
		s.executeBatch();
		assertEquals("inventory", conn.getCatalog());

		PreparedStatement use = conn.prepareStatement("USE \"sales\"");
		use.addBatch();
		assertEquals("inventory", conn.getCatalog());
		use.executeBatch();
		assertEquals("sales", conn.getCatalog());

		// a USE that failed may still have moved the session (a batch runs part): read again
		arrow.refuses = sql -> sql.startsWith("USE \"nowhere");
		catalog.set("elsewhere");
		assertThrows(SQLException.class, () -> conn.createStatement().execute("USE \"nowhere\""));
		assertEquals("elsewhere", conn.getCatalog());
	}

	// owner, 2026-10-09: the session's catalog is virtual in every mode - DBeaver's USE "?" works in each
	@Test
	void useIsSentAsWrittenInEveryMode() throws SQLException {
		for (Mode mode : Mode.values()) {
			FakeArrow arrow = node(new AtomicReference<>("sales"));
			Connection conn = new AclConnection(arrow.connection(), mode, false);
			conn.prepareStatement("USE \"x\"");
			conn.createStatement().execute("USE SCHEMA \"raw.eu\"");
			assertEquals(List.of("USE \"x\""), arrow.prepared, mode.toString());
			assertEquals(List.of("SELECT 1", "USE SCHEMA \"raw.eu\""), arrow.sent, mode.toString());
		}
	}

	// JDBC 4.3: a prepared or callable statement refuses the methods that take SQL text
	@Test
	void preparedStatementsRefuseText() throws SQLException {
		FakeArrow arrow = new FakeArrow(sql -> null);
		Connection conn = new AclConnection(arrow.connection(), Mode.NATIVE, false);
		for (PreparedStatement p : List.of(conn.prepareStatement("SELECT 1"), conn.prepareCall("SELECT 1"))) {
			assertThrows(SQLException.class, () -> p.executeQuery("DROP TABLE t"));
			assertThrows(SQLException.class, () -> p.execute("DROP TABLE t"));
			assertThrows(SQLException.class, () -> p.execute("DROP TABLE t", Statement.NO_GENERATED_KEYS));
			assertThrows(SQLException.class, () -> p.execute("DROP TABLE t", new int[] {1}));
			assertThrows(SQLException.class, () -> p.execute("DROP TABLE t", new String[] {"a"}));
			assertThrows(SQLException.class, () -> p.executeUpdate("DROP TABLE t"));
			assertThrows(SQLException.class, () -> p.executeUpdate("DROP TABLE t", Statement.NO_GENERATED_KEYS));
			assertThrows(SQLException.class, () -> p.executeUpdate("DROP TABLE t", new int[] {1}));
			assertThrows(SQLException.class, () -> p.executeUpdate("DROP TABLE t", new String[] {"a"}));
			assertThrows(SQLException.class, () -> p.executeLargeUpdate("DROP TABLE t"));
			assertThrows(SQLException.class, () -> p.addBatch("DROP TABLE t"));
		}
		assertEquals(List.of(), arrow.sent, "nothing went out unprefixed");
	}

	// review 2026-10-09: the listings pass the tool's filters on as MetadataSql composes them
	@Test
	void listingsSendExactlyTheirSql() throws SQLException {
		FakeArrow arrow = new FakeArrow(sql -> null);
		DatabaseMetaData meta = new AclConnection(arrow.connection(), Mode.MANAGE, false).getMetaData();
		meta.getColumns("sales", "raw.eu", "events", "k%");
		assertEquals(List.of(MetadataSql.columns("sales", "raw.eu", "events", "k%")), arrow.sent);
		arrow.sent.clear();
		meta.getTables("sales", "raw.eu", "ev%", new String[] {"VIEW"});
		assertEquals(List.of(MetadataSql.tables("sales", "raw.eu", "ev%", new String[] {"VIEW"})), arrow.sent);
		arrow.sent.clear();
		meta.getSchemas("sales", "raw%");
		assertEquals(List.of(MetadataSql.schemas("sales", "raw%")), arrow.sent);
		arrow.sent.clear();
		meta.getFunctions("sales", "main", "orders%");
		assertEquals(List.of(MetadataSql.functions("sales", "main", "orders%")), arrow.sent);
		arrow.sent.clear();
		meta.getProcedures("sales", "main", "shout");
		assertEquals(List.of(MetadataSql.functions("sales", "main", "shout")), arrow.sent);
		arrow.sent.clear();
		meta.getFunctionColumns("sales", "main", "orders_over", "thr%");
		assertEquals(List.of(MetadataSql.functionColumns("sales", "main", "orders_over", "thr%")), arrow.sent);
		arrow.sent.clear();
		meta.getPrimaryKeys("sales", "raw.eu", "events");
		assertEquals(List.of(MetadataSql.primaryKeys("sales", "raw.eu", "events")), arrow.sent);
		arrow.sent.clear();
		meta.getImportedKeys("sales", "main", "orders");
		assertEquals(List.of(MetadataSql.references("sales", "main", "orders", null, null, null)), arrow.sent);
		arrow.sent.clear();
		meta.getExportedKeys("sales", "main", "customers");
		assertEquals(List.of(MetadataSql.references(null, null, null, "sales", "main", "customers")), arrow.sent);
		arrow.sent.clear();
		// parent (primary key side) first, then the foreign one - the SQL's from is the foreign side
		meta.getCrossReference("sales", "main", "customers", "sales", "raw.eu", "orders");
		assertEquals(List.of(MetadataSql.references("sales", "raw.eu", "orders", "sales", "main", "customers")),
		    arrow.sent);
		assertTrue(arrow.sent.get(0).contains("r.from_object = 'raw.eu.orders'"), arrow.sent.get(0));
		assertTrue(arrow.sent.get(0).contains("r.to_object = 'customers'"), arrow.sent.get(0));
	}

	// owner, 2026-10-09: nothing is cached beyond one call
	@Test
	void listingsAreNotCached() throws SQLException {
		FakeArrow arrow = new FakeArrow(sql -> null);
		DatabaseMetaData meta = new AclConnection(arrow.connection(), Mode.DATA, false).getMetaData();
		meta.getTables("sales", "main", "%", null);
		meta.getTables("sales", "main", "%", null);
		assertEquals(2, arrow.sent.size(), arrow.sent.toString());
		arrow.sent.clear();
		meta.getCatalogs();
		assertEquals(List.of(MetadataSql.catalogs()), arrow.sent);
	}

	private static FakeArrow functionNode() {
		return new FakeArrow(sql -> {
			if (!sql.contains("acl_function_columns()")) {
				return null;
			}
			List<Object[]> rows = new ArrayList<>();
			rows.add(new Object[] {"sales", "main", "shout", "scalar", "param", 1, "text", "VARCHAR", null, null});
			if (!sql.contains("column_name LIKE") || sql.contains("column_kind = 'return'")) {
				// the node's own filter: the return value has no column_name, a LIKE alone never brings it
				rows.add(new Object[] {"sales", "main", "shout", "scalar", "return", 0, null, "VARCHAR", null, null});
			}
			return new Answer(List.of("database_name", "schema_name", "function_name", "function_type", "column_kind",
			    "position", "column_name", "data_type", "is_nullable", "comment"), rows);
		});
	}

	// review 2026-10-09: with a column pattern the return value never came
	@Test
	void theReturnValueIsAColumn() throws SQLException {
		DatabaseMetaData meta = new AclConnection(functionNode().connection(), Mode.DATA, false).getMetaData();
		ResultSet all = meta.getFunctionColumns("sales", "main", "shout", null);
		assertTrue(all.next());
		assertEquals(AclDatabaseMetaData.RETURN_VALUE, all.getString("COLUMN_NAME"));
		assertEquals(DatabaseMetaData.functionReturn, all.getShort("COLUMN_TYPE"));
		assertEquals(0, all.getInt("ORDINAL_POSITION"));
		assertEquals("shout(VARCHAR)", all.getString("SPECIFIC_NAME"));
		assertEquals("VARCHAR", all.getString("TYPE_NAME"));
		assertTrue(all.next());
		assertEquals("text", all.getString("COLUMN_NAME"));
		assertEquals(DatabaseMetaData.functionColumnIn, all.getShort("COLUMN_TYPE"));
		assertEquals(1, all.getInt("ORDINAL_POSITION"));
		assertFalse(all.next());

		ResultSet asked = meta.getFunctionColumns("sales", "main", "shout", "return%");
		assertTrue(asked.next());
		assertEquals(AclDatabaseMetaData.RETURN_VALUE, asked.getString("COLUMN_NAME"));
		assertEquals("shout(VARCHAR)", asked.getString("SPECIFIC_NAME"), "the signature still from its parameters");
		assertFalse(asked.next());

		ResultSet procedure = meta.getProcedureColumns("sales", "main", "shout", "returnValue");
		assertTrue(procedure.next());
		assertEquals(DatabaseMetaData.procedureColumnReturn, procedure.getShort("COLUMN_TYPE"));
		assertFalse(procedure.next());
	}

	// review 2026-10-09: PK_NAME named a key the referenced object never declared
	@Test
	void pkNameOnlyForADeclaredKey() throws SQLException {
		FakeArrow arrow = new FakeArrow(sql -> sql.contains("acl_references()")
		    ? new Answer(List.of("vcat", "name", "from_object", "to_object", "from_column_list", "to_column_list",
		        "key_object"),
		        List.of(new Object[] {"sales", "order_customer", "orders", "customers", List.of("id"), List.of("id"),
		                    "customers"},
		            new Object[] {"sales", "order_region", "orders", "regions", List.of("region"), List.of("code"),
		                null}))
		    : null);
		ResultSet keys =
		    new AclConnection(arrow.connection(), Mode.DATA, false).getMetaData().getImportedKeys("sales", "main", "orders");
		assertTrue(keys.next());
		assertEquals("customers", keys.getString("PKTABLE_NAME"));
		assertEquals("customers_pk", keys.getString("PK_NAME"));
		assertTrue(keys.next());
		assertEquals("regions", keys.getString("PKTABLE_NAME"));
		assertNull(keys.getString("PK_NAME"));
	}

	// review 2026-10-09: Arrow's answers for these have no columns at all
	@Test
	void whatTheNodeHasNoneOfIsEmptyWithItsColumns() throws SQLException {
		FakeArrow arrow = new FakeArrow(sql -> null);
		DatabaseMetaData meta = new AclConnection(arrow.connection(), Mode.DATA, false).getMetaData();
		assertShape(13, "INDEX_NAME", meta.getIndexInfo("sales", "main", "orders", false, true));
		assertShape(8, "PSEUDO_COLUMN", meta.getBestRowIdentifier("sales", "main", "orders", 0, true));
		assertShape(8, "PSEUDO_COLUMN", meta.getVersionColumns("sales", "main", "orders"));
		assertShape(7, "BASE_TYPE", meta.getUDTs("sales", "main", "%", null));
		assertShape(7, "IS_GRANTABLE", meta.getTablePrivileges("sales", "main", "%"));
		assertShape(8, "IS_GRANTABLE", meta.getColumnPrivileges("sales", "main", "orders", "%"));
		assertShape(12, "COLUMN_USAGE", meta.getPseudoColumns("sales", "main", "%", "%"));
		assertShape(6, "SUPERTYPE_NAME", meta.getSuperTypes("sales", "main", "%"));
		assertShape(4, "SUPERTABLE_NAME", meta.getSuperTables("sales", "main", "%"));
		assertShape(21, "SOURCE_DATA_TYPE", meta.getAttributes("sales", "main", "%", "%"));
		assertShape(4, "DESCRIPTION", meta.getClientInfoProperties());
		assertEquals(List.of(), arrow.sent);
	}

	private static void assertShape(int columns, String last, ResultSet rs) throws SQLException {
		assertEquals(columns, rs.getMetaData().getColumnCount());
		assertEquals(last, rs.getMetaData().getColumnLabel(rs.findColumn(last)));
		assertFalse(rs.next());
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
