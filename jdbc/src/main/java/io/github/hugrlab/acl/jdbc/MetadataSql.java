// Adapted from duckdb-java (MIT, Copyright 2018-2025 Stichting DuckDB Foundation):
// DuckDBDatabaseMetaData - the JDBC filter rules (appendEqualsQual / appendLikeQual). See THIRD-PARTY.md.
package io.github.hugrlab.acl.jdbc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The SQL behind every {@link AclDatabaseMetaData} listing (spec 006), on the principal's own surfaces.
 *
 * <p>Two rules shape it. <b>No function calls</b> beyond the listing table functions themselves - only
 * column references, {@code =}, {@code LIKE ... ESCAPE '\'}, {@code IS NULL}, {@code ORDER BY} and
 * literals (quotes doubled): under a principal every call passes the function gate (duckdb-acl spec
 * 072), and an ordinary role must be able to read its own tree; types and CASEs are mapped in Java.
 * <b>Lazy by construction</b> - the node can hold very many schemas and objects, so every filter a tool
 * passes is in the WHERE the node runs, never applied to a full listing afterwards.
 *
 * <p>JDBC's filter rules, as duckdb-java has them: a null name or pattern does not filter, {@code ""}
 * means "without one" ({@code IS NULL}).
 */
final class MetadataSql {
	private MetadataSql() {
	}

	static final String TYPE_TABLE = "BASE TABLE";
	static final String TYPE_VIEW = "VIEW";
	static final String TYPE_TEMPORARY = "LOCAL TEMPORARY";

	/** A WHERE clause under construction. */
	static final class Where {
		private final List<String> conditions = new ArrayList<>();

		Where eq(String column, String value) {
			if (value != null) {
				conditions.add(value.isEmpty() ? column + " IS NULL" : column + " = " + AclSql.literal(value));
			}
			return this;
		}

		Where like(String column, String pattern) {
			if (pattern != null) {
				conditions.add(pattern.isEmpty() ? column + " IS NULL"
				                                 : column + " LIKE " + AclSql.literal(AclSql.likePattern(pattern))
				                                       + " ESCAPE '\\'");
			}
			return this;
		}

		Where raw(String condition) {
			conditions.add(condition);
			return this;
		}

		@Override
		public String toString() {
			return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
		}
	}

	/**
	 * Catalogs and schemas from {@code information_schema.schemata}: its {@code schema_name} is a nested
	 * schema's whole path ({@code raw.eu}) - the text {@code duckdb_tables()} / {@code duckdb_columns()} /
	 * {@code duckdb_functions()} carry in theirs - where {@code duckdb_schemas()} names only the leaf
	 * ({@code eu}) as duckdb does natively (duckdb-acl spec 115).
	 */
	static String catalogs() {
		return "SELECT DISTINCT catalog_name FROM information_schema.schemata ORDER BY catalog_name";
	}

	static String schemas(String catalog, String schemaPattern) {
		return "SELECT catalog_name, schema_name FROM information_schema.schemata"
		    + new Where().eq("catalog_name", catalog).like("schema_name", schemaPattern)
		    + " ORDER BY catalog_name, schema_name";
	}

	/** null when the types asked for are none this node has. */
	static String tables(String catalog, String schemaPattern, String namePattern, String[] types) {
		List<String> wanted = new ArrayList<>();
		if (types == null) {
			wanted = List.of(TYPE_TABLE, TYPE_TEMPORARY, TYPE_VIEW);
		} else {
			for (String type : types) {
				String t = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
				switch (t) {
					case "TABLE", TYPE_TABLE -> addOnce(wanted, TYPE_TABLE);
					case TYPE_TEMPORARY, "TEMPORARY", "GLOBAL TEMPORARY" -> addOnce(wanted, TYPE_TEMPORARY);
					case TYPE_VIEW -> addOnce(wanted, TYPE_VIEW);
					default -> {
					}
				}
			}
		}
		if (wanted.isEmpty()) {
			return null;
		}
		// duckdb-acl spec 121: information_schema.tables - one listing, the cheapest the node has for a schema's
		// objects (duckdb_tables UNION duckdb_views cost an order of magnitude more at scale); its TABLE_TYPE is
		// JDBC's (BASE TABLE / LOCAL TEMPORARY / VIEW)
		Where where = new Where().eq("table_catalog", catalog).like("table_schema", schemaPattern).like("table_name",
		    namePattern);
		if (wanted.size() < 3) {
			List<String> quoted = new ArrayList<>();
			for (String type : wanted) {
				quoted.add("'" + type + "'");
			}
			where.raw("table_type IN (" + String.join(", ", quoted) + ")");
		}
		// JDBC's order: TABLE_TYPE (BASE TABLE, LOCAL TEMPORARY, VIEW), then catalog, schema, name
		return "SELECT table_catalog, table_schema, table_name, table_type, \"TABLE_COMMENT\" AS comment"
		    + " FROM information_schema.tables" + where
		    + " ORDER BY table_type, table_catalog, table_schema, table_name";
	}

	private static void addOnce(List<String> list, String value) {
		if (!list.contains(value)) {
			list.add(value);
		}
	}

	static String columns(String catalog, String schemaPattern, String tablePattern, String columnPattern) {
		return "SELECT database_name, schema_name, table_name, column_name, column_index, comment, column_default,"
		    + " is_nullable, data_type, is_generated FROM duckdb_columns()"
		    + new Where().eq("database_name", catalog).like("schema_name", schemaPattern).like("table_name", tablePattern)
		          .like("column_name", columnPattern)
		    + " ORDER BY database_name, schema_name, table_name, column_index";
	}

	static String functions(String catalog, String schemaPattern, String namePattern) {
		return "SELECT database_name, schema_name, function_name, function_type, comment, description, parameter_types"
		    + " FROM duckdb_functions()"
		    + new Where().eq("database_name", catalog).like("schema_name", schemaPattern).like("function_name",
		        namePattern)
		    + " ORDER BY database_name, schema_name, function_name";
	}

	/**
	 * A function's parameters always come with it, whatever the column pattern: they make its
	 * SPECIFIC_NAME (the signature), and the pattern then drops those it does not name. Its return value
	 * too: the listing does not name it, JDBC tools call it {@code returnValue}.
	 */
	static String functionColumns(String catalog, String schemaPattern, String functionPattern,
	    String columnPattern) {
		Where where = new Where().eq("database_name", catalog).like("schema_name", schemaPattern).like("function_name",
		    functionPattern);
		if (columnPattern != null && !columnPattern.equals("%")) {
			// the return value has no name in the listing: it is matched in Java, as returnValue
			where.raw("(column_kind = 'param' OR column_kind = 'return' OR column_name LIKE "
			          + AclSql.literal(AclSql.likePattern(columnPattern)) + " ESCAPE '\\')");
		}
		return "SELECT database_name, schema_name, function_name, function_type, column_kind, position, column_name,"
		    + " data_type, is_nullable, comment FROM acl_function_columns()" + where
		    + " ORDER BY database_name, schema_name, function_name, column_kind, position";
	}

	static String types() {
		return "SELECT type_name, logical_type FROM duckdb_types() WHERE database_name = 'system' ORDER BY type_name";
	}

	/** The declared keys (duckdb-acl spec 048); {@code object} is the path inside the catalog. */
	static String primaryKeys(String catalog, String schema, String table) {
		Where where = new Where().raw("kind = 'relation'").eq("vcat", catalog);
		objectPath(where, "object", schema, table);
		return "SELECT vcat, object, key_sequence, \"column\" FROM acl_keys()" + where + " ORDER BY vcat, object, \"column\"";
	}

	/**
	 * The declared references (duckdb-acl spec 022) that are foreign keys: to a relation, by column
	 * pairs. {@code from} is the referencing (foreign key) side, {@code to} the referenced one;
	 * {@code to_has_key} is true when the referenced object declares a key the role sees (spec 048) - the node's and
	 * a join, no call.
	 */
	static String references(String fromCatalog, String fromSchema, String fromTable, String toCatalog,
	    String toSchema, String toTable) {
		Where where = new Where().raw("r.to_kind = 'relation'").raw("r.expression IS NULL").eq("r.vcat", fromCatalog);
		if (toCatalog != null && !toCatalog.equals(fromCatalog)) {
			where.eq("r.vcat", toCatalog);
		}
		objectPath(where, "r.from_object", fromSchema, fromTable);
		objectPath(where, "r.to_object", toSchema, toTable);
		// duckdb-acl spec 121: whether the far end declares a key the role sees is the listing's own column - a
		// join on every key of the node cost more than the references themselves
		return "SELECT r.vcat, r.name, r.from_object, r.to_object, r.from_column_list, r.to_column_list,"
		    + " r.to_has_key FROM acl_references() r" + where + " ORDER BY r.vcat, r.name";
	}

	/**
	 * An object's path in its catalog: {@code t} at the root ({@code main}), {@code raw.eu.t} in a nested
	 * schema. Without a schema, the name at any depth.
	 */
	private static void objectPath(Where where, String column, String schema, String table) {
		if (table == null) {
			if (schema == null) {
				return;
			}
			if (schema.isEmpty()) {
				where.raw(column + " IS NULL"); // JDBC: "" is "without a schema" - every object has one
			} else if (schema.equals("main")) {
				where.raw(column + " NOT LIKE '%.%'");
			} else {
				// directly in that schema, not in one nested under it (raw, not raw.eu)
				String inside = AclSql.escapeLike(schema) + ".";
				where.raw(column + " LIKE " + AclSql.literal(inside + "%") + " ESCAPE '\\'");
				where.raw(column + " NOT LIKE " + AclSql.literal(inside + "%.%") + " ESCAPE '\\'");
			}
			return;
		}
		if (schema == null) {
			where.raw("(" + column + " = " + AclSql.literal(table) + " OR " + column + " LIKE "
			          + AclSql.literal("%." + AclSql.escapeLike(table)) + " ESCAPE '\\')");
		} else if (schema.isEmpty()) {
			where.raw(column + " IS NULL");
		} else {
			where.eq(column, schema.equals("main") ? table : schema + "." + table);
		}
	}

	/** {@code raw.eu.t} → {@code [raw.eu, t]}; {@code t} → {@code [main, t]}. */
	static String[] splitPath(String path) {
		int dot = path.lastIndexOf('.');
		return dot < 0 ? new String[] {"main", path} : new String[] {path.substring(0, dot), path.substring(dot + 1)};
	}
}
