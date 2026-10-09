package io.github.hugrlab.acl.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MetadataSqlTest {
	static final Set<String> LISTINGS = Set.of("duckdb_tables", "duckdb_views", "duckdb_columns",
	    "duckdb_functions", "duckdb_types", "acl_function_columns", "acl_keys", "acl_references");
	private static final Pattern CALL = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");

	/** Every metadata statement: only the listing table functions are called (the function gate). */
	static void onlyListingsAreCalled(String sql) {
		String outsideLiterals = sql.replaceAll("'(?:[^']|'')*'", "''");
		Matcher m = CALL.matcher(outsideLiterals);
		while (m.find()) {
			if (Set.of("AND", "OR", "NOT", "JOIN").contains(m.group(1).toUpperCase(java.util.Locale.ROOT))) {
				continue; // a parenthesised condition or a subquery, no call
			}
			assertTrue(LISTINGS.contains(m.group(1)), "a function call in metadata SQL: " + m.group(1) + " in " + sql);
		}
		assertFalse(outsideLiterals.contains("?"), "literals, never parameters: " + sql);
	}

	static List<String> all() {
		return List.of(MetadataSql.catalogs(), MetadataSql.schemas("sales", "raw%"),
		    MetadataSql.tables("sales", "main", "cust%", null), MetadataSql.columns("sales", "main", "customers", "%"),
		    MetadataSql.functions("sales", "main", "orders\\_over"),
		    MetadataSql.functionColumns("sales", "main", "orders_over", "thr%"), MetadataSql.types(),
		    MetadataSql.primaryKeys("sales", "main", "customers"),
		    MetadataSql.references("sales", "raw.eu", "events", "sales", null, "customers"));
	}

	@Test
	void noFunctionCallsButTheListings() {
		for (String sql : all()) {
			onlyListingsAreCalled(sql);
		}
	}

	// owner, 2026-10-09: a listing fetches only what was asked - every filter in the WHERE the node runs
	@Test
	void theFiltersAreInTheSql() {
		assertEquals("SELECT catalog_name, schema_name FROM information_schema.schemata"
		                 + " WHERE catalog_name = 'sales' AND schema_name LIKE 'raw%' ESCAPE '\\'"
		                 + " ORDER BY catalog_name, schema_name",
		    MetadataSql.schemas("sales", "raw%"));
		String tables = MetadataSql.tables("sales", "main", "cust%", null);
		assertTrue(tables.contains("FROM duckdb_tables() WHERE database_name = 'sales' AND schema_name LIKE 'main'"
		                           + " ESCAPE '\\' AND table_name LIKE 'cust%' ESCAPE '\\'"),
		    tables);
		assertTrue(tables.contains("FROM duckdb_views() WHERE database_name = 'sales' AND schema_name LIKE 'main'"
		                           + " ESCAPE '\\' AND view_name LIKE 'cust%' ESCAPE '\\'"),
		    tables);
		String columns = MetadataSql.columns("sales", "raw.eu", "events", "k%");
		assertTrue(columns.contains("WHERE database_name = 'sales' AND schema_name LIKE 'raw.eu' ESCAPE '\\'"
		                            + " AND table_name LIKE 'events' ESCAPE '\\' AND column_name LIKE 'k%' ESCAPE '\\'"),
		    columns);
		String functions = MetadataSql.functions("sales", "main", "orders\\_over");
		assertTrue(functions.contains("WHERE database_name = 'sales' AND schema_name LIKE 'main' ESCAPE '\\'"
		                              + " AND function_name LIKE 'orders\\_over' ESCAPE '\\'"),
		    functions);
		String fc = MetadataSql.functionColumns("sales", "main", "orders_over", "thr%");
		assertTrue(fc.contains("database_name = 'sales'") && fc.contains("function_name LIKE 'orders_over'")
		               && fc.contains("(column_kind = 'param' OR column_kind = 'return' OR column_name LIKE 'thr%' ESCAPE '\\')"),
		    fc);
		assertTrue(MetadataSql.primaryKeys("sales", "main", "customers").contains(
		    "WHERE kind = 'relation' AND vcat = 'sales' AND object = 'customers'"));
		assertTrue(MetadataSql.primaryKeys("sales", "raw.eu", "events").contains("object = 'raw.eu.events'"));
		String refs = MetadataSql.references("sales", "raw.eu", "events", null, null, "customers");
		assertTrue(refs.contains("r.vcat = 'sales'") && refs.contains("r.from_object = 'raw.eu.events'")
		               && refs.contains("(r.to_object = 'customers' OR r.to_object LIKE '%.customers' ESCAPE '\\')"),
		    refs);
	}

	// review 2026-10-09: with a column pattern the return value (no column_name) never came
	@Test
	void functionColumnsBringTheReturnValue() {
		assertTrue(MetadataSql.functionColumns(null, null, "shout", "ret%").contains(
		    "(column_kind = 'param' OR column_kind = 'return' OR column_name LIKE 'ret%' ESCAPE '\\')"));
	}

	// review 2026-10-09: a pattern ending in a lone escape is an error on the node
	@Test
	void aTrailingBackslashIsLiteral() {
		assertTrue(MetadataSql.schemas("sales", "raw\\").contains("schema_name LIKE 'raw\\\\' ESCAPE"),
		    MetadataSql.schemas("sales", "raw\\"));
		assertTrue(MetadataSql.functionColumns(null, null, "f", "a\\").contains("column_name LIKE 'a\\\\' ESCAPE"));
	}

	// review 2026-10-09: schema raw without a table matched raw.eu's objects too
	@Test
	void aSchemaWithoutTableIsThatSchemaOnly() {
		String keys = MetadataSql.primaryKeys("sales", "raw", null);
		assertTrue(keys.contains("object LIKE 'raw.%' ESCAPE '\\' AND object NOT LIKE 'raw.%.%' ESCAPE '\\'"), keys);
		assertTrue(MetadataSql.primaryKeys("sales", "main", null).contains("object NOT LIKE '%.%'"));
		assertTrue(MetadataSql.primaryKeys("sales", "", null).contains("object IS NULL"));
		assertFalse(MetadataSql.primaryKeys("sales", null, null).contains("object "), "no filter on the object");
		// the key's name comes from the referenced object's declared key, joined - not composed
		String refs = MetadataSql.references(null, null, null, "sales", "main", "customers");
		assertTrue(refs.contains("LEFT JOIN (SELECT DISTINCT vcat, object FROM acl_keys() WHERE kind = 'relation') k"
		                         + " ON k.vcat = r.vcat AND k.object = r.to_object"), refs);
	}

	// duckdb-acl spec 115: duckdb_schemas() names a nested schema by its leaf (eu), as duckdb does;
	// information_schema.schemata keeps the path (raw.eu) the other listings carry
	@Test
	void schemasAreThePaths() {
		assertTrue(MetadataSql.schemas("sales", null).contains("FROM information_schema.schemata"));
		assertTrue(MetadataSql.catalogs().contains("FROM information_schema.schemata"));
		for (String sql : all()) {
			assertFalse(sql.contains("duckdb_schemas"), sql);
		}
	}

	@Test
	void jdbcFilterRules() {
		// null: no filter; "": IS NULL
		assertEquals("SELECT catalog_name, schema_name FROM information_schema.schemata ORDER BY catalog_name, schema_name",
		    MetadataSql.schemas(null, null));
		assertTrue(MetadataSql.schemas("", null).contains("WHERE catalog_name IS NULL"));
		assertTrue(MetadataSql.columns(null, "", null, null).contains("WHERE schema_name IS NULL"));
		// quotes doubled
		assertTrue(MetadataSql.schemas("o'brien", null).contains("catalog_name = 'o''brien'"));
	}

	@Test
	void tableTypesChooseTheBranches() {
		String views = MetadataSql.tables(null, null, null, new String[] {"VIEW"});
		assertFalse(views.contains("duckdb_tables()"));
		assertTrue(views.contains("duckdb_views()"));
		String base = MetadataSql.tables(null, null, null, new String[] {"TABLE"});
		assertTrue(base.contains("duckdb_tables() WHERE temporary = false"), base);
		assertFalse(base.contains("duckdb_views()"));
		assertNull(MetadataSql.tables(null, null, null, new String[] {"SYNONYM"}));
	}

	@Test
	void paths() {
		assertEquals(List.of("main", "orders"), List.of(MetadataSql.splitPath("orders")));
		assertEquals(List.of("raw.eu", "events"), List.of(MetadataSql.splitPath("raw.eu.events")));
	}
}
