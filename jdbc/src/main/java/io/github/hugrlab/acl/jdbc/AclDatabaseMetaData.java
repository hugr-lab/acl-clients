// Adapted from duckdb-java (MIT, Copyright 2018-2025 Stichting DuckDB Foundation):
// DuckDBDatabaseMetaData - the static values and the shape of the listings. See THIRD-PARTY.md.
package io.github.hugrlab.acl.jdbc;

import io.github.hugrlab.acl.jdbc.RowsResultSet.ColumnSpec;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What a tool learns about the node (spec 006). Arrow's answers are kept where they are right - the
 * SqlInfo values the node now sends (duckdb-acl spec 115: quoting, keywords, escape, terms,
 * catalog-at-start, transactions, read-only); the rest are duckdb-java's static values, and the
 * listings run as SQL on the principal's own surfaces ({@link MetadataSql}), answered as a
 * {@link RowsResultSet} whose columns are JDBC's whatever the rows. The metadata SQL is never prefixed
 * by {@code acl.mode}: the tree is always the principal's virtual catalog.
 */
final class AclDatabaseMetaData extends ForwardingDatabaseMetaData {
	private final AclConnection connection;

	AclDatabaseMetaData(AclConnection connection, DatabaseMetaData arrow) {
		super(arrow);
		this.connection = connection;
	}

	// --- static values (duckdb-java's) ---------------------------------------------------------

	@Override
	public Connection getConnection() {
		return connection;
	}

	@Override
	public int getDefaultTransactionIsolation() {
		return Connection.TRANSACTION_REPEATABLE_READ;
	}

	@Override
	public boolean supportsTransactionIsolationLevel(int level) {
		return level < Connection.TRANSACTION_SERIALIZABLE;
	}

	@Override
	public boolean storesMixedCaseIdentifiers() {
		return true;
	}

	@Override
	public boolean storesMixedCaseQuotedIdentifiers() {
		return true;
	}

	@Override
	public boolean storesUpperCaseIdentifiers() {
		return false;
	}

	@Override
	public boolean storesUpperCaseQuotedIdentifiers() {
		return false;
	}

	@Override
	public boolean storesLowerCaseIdentifiers() {
		return false;
	}

	@Override
	public boolean storesLowerCaseQuotedIdentifiers() {
		return false;
	}

	@Override
	public String getCatalogSeparator() {
		return ".";
	}

	@Override
	public ResultSet getTableTypes() {
		List<Object[]> rows = new ArrayList<>();
		for (String type : List.of(MetadataSql.TYPE_TABLE, MetadataSql.TYPE_TEMPORARY, MetadataSql.TYPE_VIEW)) {
			rows.add(new Object[] {type});
		}
		return new RowsResultSet(List.of(ColumnSpec.text("TABLE_TYPE")), rows);
	}

	// --- listings -------------------------------------------------------------------------------

	/** The rows of one metadata statement, by lower-case column name; nested values as plain Java. */
	List<Map<String, Object>> query(String sql) throws SQLException {
		List<Map<String, Object>> out = new ArrayList<>();
		if (sql == null) {
			return out;
		}
		connection.noteStatement(sql);
		try (Statement s = connection.arrow().createStatement(); ResultSet rs = s.executeQuery(sql)) {
			ResultSetMetaData meta = rs.getMetaData();
			int n = meta.getColumnCount();
			String[] names = new String[n];
			for (int i = 0; i < n; i++) {
				names[i] = meta.getColumnLabel(i + 1).toLowerCase(Locale.ROOT);
			}
			while (rs.next()) {
				Map<String, Object> row = new LinkedHashMap<>();
				for (int i = 0; i < n; i++) {
					row.put(names[i], NestedValues.normalize(rs.getObject(i + 1), null));
				}
				out.add(row);
			}
		}
		return out;
	}

	@Override
	public ResultSet getCatalogs() throws SQLException {
		List<Object[]> rows = new ArrayList<>();
		for (Map<String, Object> r : query(MetadataSql.catalogs())) {
			rows.add(new Object[] {r.get("database_name")});
		}
		return new RowsResultSet(List.of(ColumnSpec.text("TABLE_CAT")), rows);
	}

	@Override
	public ResultSet getSchemas() throws SQLException {
		return getSchemas(null, null);
	}

	@Override
	public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
		List<Object[]> rows = new ArrayList<>();
		for (Map<String, Object> r : query(MetadataSql.schemas(catalog, schemaPattern))) {
			rows.add(new Object[] {r.get("schema_name"), r.get("database_name")});
		}
		return new RowsResultSet(List.of(ColumnSpec.text("TABLE_SCHEM"), ColumnSpec.text("TABLE_CATALOG")), rows);
	}

	static final List<ColumnSpec> TABLE_COLUMNS = List.of(ColumnSpec.text("TABLE_CAT"), ColumnSpec.text("TABLE_SCHEM"),
	    ColumnSpec.text("TABLE_NAME"), ColumnSpec.text("TABLE_TYPE"), ColumnSpec.text("REMARKS"),
	    ColumnSpec.text("TYPE_CAT"), ColumnSpec.text("TYPE_SCHEM"), ColumnSpec.text("TYPE_NAME"),
	    ColumnSpec.text("SELF_REFERENCING_COL_NAME"), ColumnSpec.text("REF_GENERATION"));

	@Override
	public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types)
	    throws SQLException {
		List<Object[]> rows = new ArrayList<>();
		for (Map<String, Object> r : query(MetadataSql.tables(catalog, schemaPattern, tableNamePattern, types))) {
			String type = truthy(r.get("is_view")) ? MetadataSql.TYPE_VIEW
			              : truthy(r.get("temporary")) ? MetadataSql.TYPE_TEMPORARY
			                                           : MetadataSql.TYPE_TABLE;
			rows.add(new Object[] {r.get("database_name"), r.get("schema_name"), r.get("object_name"), type,
			    r.get("comment"), null, null, null, null, null});
		}
		return new RowsResultSet(TABLE_COLUMNS, rows);
	}

	static final List<ColumnSpec> COLUMN_COLUMNS = List.of(ColumnSpec.text("TABLE_CAT"),
	    ColumnSpec.text("TABLE_SCHEM"), ColumnSpec.text("TABLE_NAME"), ColumnSpec.text("COLUMN_NAME"),
	    ColumnSpec.integer("DATA_TYPE"), ColumnSpec.text("TYPE_NAME"), ColumnSpec.integer("COLUMN_SIZE"),
	    ColumnSpec.integer("BUFFER_LENGTH"), ColumnSpec.integer("DECIMAL_DIGITS"), ColumnSpec.integer("NUM_PREC_RADIX"),
	    ColumnSpec.integer("NULLABLE"), ColumnSpec.text("REMARKS"), ColumnSpec.text("COLUMN_DEF"),
	    ColumnSpec.integer("SQL_DATA_TYPE"), ColumnSpec.integer("SQL_DATETIME_SUB"),
	    ColumnSpec.integer("CHAR_OCTET_LENGTH"), ColumnSpec.integer("ORDINAL_POSITION"), ColumnSpec.text("IS_NULLABLE"),
	    ColumnSpec.text("SCOPE_CATALOG"), ColumnSpec.text("SCOPE_SCHEMA"), ColumnSpec.text("SCOPE_TABLE"),
	    ColumnSpec.small("SOURCE_DATA_TYPE"), ColumnSpec.text("IS_AUTOINCREMENT"),
	    ColumnSpec.text("IS_GENERATEDCOLUMN"));

	@Override
	public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern,
	    String columnNamePattern) throws SQLException {
		List<Object[]> rows = new ArrayList<>();
		for (Map<String, Object> r :
		     query(MetadataSql.columns(catalog, schemaPattern, tableNamePattern, columnNamePattern))) {
			String typeText = text(r.get("data_type"));
			DuckTypes.Type t = DuckTypes.parse(typeText);
			Object nullable = r.get("is_nullable");
			rows.add(new Object[] {r.get("database_name"), r.get("schema_name"), r.get("table_name"),
			    r.get("column_name"), t.jdbcType(), typeText, t.size(), null, t.isNumeric() ? t.digits() : null,
			    t.isNumeric() ? 10 : null, nullableCode(nullable), r.get("comment"), r.get("column_default"), null, null,
			    t.jdbcType() == Types.VARCHAR ? t.size() : null, intOrNull(r.get("column_index")),
			    isNullableText(nullable), null, null, null, null, "NO", truthy(r.get("is_generated")) ? "YES" : "NO"});
		}
		return new RowsResultSet(COLUMN_COLUMNS, rows);
	}

	// --- functions ------------------------------------------------------------------------------

	static final List<ColumnSpec> FUNCTION_COLUMNS = List.of(ColumnSpec.text("FUNCTION_CAT"),
	    ColumnSpec.text("FUNCTION_SCHEM"), ColumnSpec.text("FUNCTION_NAME"), ColumnSpec.text("REMARKS"),
	    ColumnSpec.small("FUNCTION_TYPE"), ColumnSpec.text("SPECIFIC_NAME"));

	static final List<ColumnSpec> PROCEDURE_COLUMNS = List.of(ColumnSpec.text("PROCEDURE_CAT"),
	    ColumnSpec.text("PROCEDURE_SCHEM"), ColumnSpec.text("PROCEDURE_NAME"), ColumnSpec.text("RESERVED1"),
	    ColumnSpec.text("RESERVED2"), ColumnSpec.text("RESERVED3"), ColumnSpec.text("REMARKS"),
	    ColumnSpec.small("PROCEDURE_TYPE"), ColumnSpec.text("SPECIFIC_NAME"));

	@Override
	public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern)
	    throws SQLException {
		List<Object[]> rows = new ArrayList<>();
		for (Map<String, Object> r : query(MetadataSql.functions(catalog, schemaPattern, functionNamePattern))) {
			short type = returnsTable(r.get("function_type")) ? (short) functionReturnsTable : (short) functionNoTable;
			rows.add(new Object[] {r.get("database_name"), r.get("schema_name"), r.get("function_name"), remarks(r),
			    type, specificName(text(r.get("function_name")), r.get("parameter_types"))});
		}
		rows.sort(SPECIFIC_ORDER);
		return new RowsResultSet(FUNCTION_COLUMNS, rows);
	}

	/**
	 * A duckdb function is a function, not a stored procedure; the same rows answer here so a tool that
	 * only reads procedures (most generic ones) shows them - SPECIFIC_NAME is the same in both listings,
	 * so a tool that reads both can tell they are one object.
	 */
	@Override
	public ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern)
	    throws SQLException {
		List<Object[]> rows = new ArrayList<>();
		for (Map<String, Object> r : query(MetadataSql.functions(catalog, schemaPattern, procedureNamePattern))) {
			rows.add(new Object[] {r.get("database_name"), r.get("schema_name"), r.get("function_name"), null, null,
			    null, remarks(r), (short) procedureReturnsResult,
			    specificName(text(r.get("function_name")), r.get("parameter_types"))});
		}
		rows.sort(col(0).thenComparing(col(1)).thenComparing(col(2)).thenComparing(col(8)));
		return new RowsResultSet(PROCEDURE_COLUMNS, rows);
	}

	private static final Comparator<Object[]> SPECIFIC_ORDER =
	    col(0).thenComparing(col(1)).thenComparing(col(2)).thenComparing(col(5));

	/** Rows by one text column, nulls first. */
	private static Comparator<Object[]> col(int i) {
		return Comparator.comparing((Object[] row) -> text(row[i]), Comparator.nullsFirst(Comparator.naturalOrder()));
	}

	static final List<ColumnSpec> FUNCTION_COLUMN_COLUMNS = List.of(ColumnSpec.text("FUNCTION_CAT"),
	    ColumnSpec.text("FUNCTION_SCHEM"), ColumnSpec.text("FUNCTION_NAME"), ColumnSpec.text("COLUMN_NAME"),
	    ColumnSpec.small("COLUMN_TYPE"), ColumnSpec.integer("DATA_TYPE"), ColumnSpec.text("TYPE_NAME"),
	    ColumnSpec.integer("PRECISION"), ColumnSpec.integer("LENGTH"), ColumnSpec.small("SCALE"),
	    ColumnSpec.small("RADIX"), ColumnSpec.small("NULLABLE"), ColumnSpec.text("REMARKS"),
	    ColumnSpec.integer("CHAR_OCTET_LENGTH"), ColumnSpec.integer("ORDINAL_POSITION"), ColumnSpec.text("IS_NULLABLE"),
	    ColumnSpec.text("SPECIFIC_NAME"));

	static final List<ColumnSpec> PROCEDURE_COLUMN_COLUMNS = List.of(ColumnSpec.text("PROCEDURE_CAT"),
	    ColumnSpec.text("PROCEDURE_SCHEM"), ColumnSpec.text("PROCEDURE_NAME"), ColumnSpec.text("COLUMN_NAME"),
	    ColumnSpec.small("COLUMN_TYPE"), ColumnSpec.integer("DATA_TYPE"), ColumnSpec.text("TYPE_NAME"),
	    ColumnSpec.integer("PRECISION"), ColumnSpec.integer("LENGTH"), ColumnSpec.small("SCALE"),
	    ColumnSpec.small("RADIX"), ColumnSpec.small("NULLABLE"), ColumnSpec.text("REMARKS"),
	    ColumnSpec.text("COLUMN_DEF"), ColumnSpec.integer("SQL_DATA_TYPE"), ColumnSpec.integer("SQL_DATETIME_SUB"),
	    ColumnSpec.integer("CHAR_OCTET_LENGTH"), ColumnSpec.integer("ORDINAL_POSITION"), ColumnSpec.text("IS_NULLABLE"),
	    ColumnSpec.text("SPECIFIC_NAME"));

	/** One parameter, result column or return value of a function, as acl_function_columns() lists it. */
	record FunctionColumn(String catalog, String schema, String function, String kind, int position, String name,
	    String type, Object nullable, String comment, String specificName) {
	}

	/**
	 * The rows of {@code acl_function_columns()} for the functions asked, each with its function's
	 * SPECIFIC_NAME, in JDBC's order: the return value, the parameters, the result columns.
	 */
	List<FunctionColumn> functionColumns(String catalog, String schemaPattern, String functionPattern,
	    String columnPattern) throws SQLException {
		List<Map<String, Object>> rows =
		    query(MetadataSql.functionColumns(catalog, schemaPattern, functionPattern, columnPattern));
		Map<String, List<String>> signatures = new LinkedHashMap<>();
		for (Map<String, Object> r : rows) {
			signatures.computeIfAbsent(functionKey(r), k -> new ArrayList<>());
			if ("param".equals(r.get("column_kind"))) {
				signatures.get(functionKey(r)).add(text(r.get("data_type")));
			}
		}
		Pattern names = columnPattern == null ? null : likePattern(columnPattern);
		List<FunctionColumn> out = new ArrayList<>();
		for (Map<String, Object> r : rows) {
			String name = text(r.get("column_name"));
			String kind = text(r.get("column_kind"));
			String shown = "return".equals(kind) && name == null ? RETURN_VALUE : name;
			if (names != null && (shown == null || !names.matcher(shown).matches())) {
				continue; // a parameter fetched for the signature, not asked for
			}
			String function = text(r.get("function_name"));
			out.add(new FunctionColumn(text(r.get("database_name")), text(r.get("schema_name")), function, kind,
			    intOrZero(r.get("position")), name, text(r.get("data_type")), r.get("is_nullable"),
			    text(r.get("comment")), function + "(" + String.join(", ", signatures.get(functionKey(r))) + ")"));
		}
		out.sort(Comparator.comparing(FunctionColumn::catalog, Comparator.nullsFirst(Comparator.naturalOrder()))
		             .thenComparing(FunctionColumn::schema, Comparator.nullsFirst(Comparator.naturalOrder()))
		             .thenComparing(FunctionColumn::function)
		             .thenComparing(FunctionColumn::specificName)
		             .thenComparing(c -> kindOrder(c.kind()))
		             .thenComparing(FunctionColumn::position));
		return out;
	}

	private static String functionKey(Map<String, Object> r) {
		return r.get("database_name") + "\u0000" + r.get("schema_name") + "\u0000" + r.get("function_name") + "\u0000"
		    + r.get("function_type");
	}

	private static int kindOrder(String kind) {
		return switch (kind == null ? "" : kind) {
			case "return" -> 0;
			case "param" -> 1;
			default -> 2;
		};
	}

	@Override
	public ResultSet getFunctionColumns(String catalog, String schemaPattern, String functionNamePattern,
	    String columnNamePattern) throws SQLException {
		List<Object[]> rows = new ArrayList<>();
		for (FunctionColumn c : functionColumns(catalog, schemaPattern, functionNamePattern, columnNamePattern)) {
			short kind = switch (c.kind()) {
				case "param" -> (short) functionColumnIn;
				case "return" -> (short) functionReturn;
				default -> (short) functionColumnResult;
			};
			DuckTypes.Type t = DuckTypes.parse(c.type());
			rows.add(new Object[] {c.catalog(), c.schema(), c.function(), columnName(c), kind, t.jdbcType(), c.type(),
			    t.size(), null, t.isNumeric() && t.digits() != null ? t.digits().shortValue() : null,
			    t.isNumeric() ? (short) 10 : null, nullableCode(c.nullable()).shortValue(), c.comment(), null,
			    "return".equals(c.kind()) ? 0 : c.position(), isNullableText(c.nullable()), c.specificName()});
		}
		return new RowsResultSet(FUNCTION_COLUMN_COLUMNS, rows);
	}

	@Override
	public ResultSet getProcedureColumns(String catalog, String schemaPattern, String procedureNamePattern,
	    String columnNamePattern) throws SQLException {
		List<Object[]> rows = new ArrayList<>();
		for (FunctionColumn c : functionColumns(catalog, schemaPattern, procedureNamePattern, columnNamePattern)) {
			short kind = switch (c.kind()) {
				case "param" -> (short) procedureColumnIn;
				case "return" -> (short) procedureColumnReturn;
				default -> (short) procedureColumnResult;
			};
			DuckTypes.Type t = DuckTypes.parse(c.type());
			rows.add(new Object[] {c.catalog(), c.schema(), c.function(), columnName(c), kind, t.jdbcType(), c.type(),
			    t.size(), null, t.isNumeric() && t.digits() != null ? t.digits().shortValue() : null,
			    t.isNumeric() ? (short) 10 : null, nullableCode(c.nullable()).shortValue(), c.comment(), null, null, null,
			    null, "return".equals(c.kind()) ? 0 : c.position(), isNullableText(c.nullable()), c.specificName()});
		}
		return new RowsResultSet(PROCEDURE_COLUMN_COLUMNS, rows);
	}

	/** The name JDBC tools show for a function's return value (as PgJDBC names it). */
	static final String RETURN_VALUE = "returnValue";

	private static String columnName(FunctionColumn c) {
		return "return".equals(c.kind()) && c.name() == null ? RETURN_VALUE : c.name();
	}

	// --- types ----------------------------------------------------------------------------------

	static final List<ColumnSpec> TYPE_INFO_COLUMNS = List.of(ColumnSpec.text("TYPE_NAME"),
	    ColumnSpec.integer("DATA_TYPE"), ColumnSpec.integer("PRECISION"), ColumnSpec.text("LITERAL_PREFIX"),
	    ColumnSpec.text("LITERAL_SUFFIX"), ColumnSpec.text("CREATE_PARAMS"), ColumnSpec.small("NULLABLE"),
	    ColumnSpec.bool("CASE_SENSITIVE"), ColumnSpec.small("SEARCHABLE"), ColumnSpec.bool("UNSIGNED_ATTRIBUTE"),
	    ColumnSpec.bool("FIXED_PREC_SCALE"), ColumnSpec.bool("AUTO_INCREMENT"), ColumnSpec.text("LOCAL_TYPE_NAME"),
	    ColumnSpec.small("MINIMUM_SCALE"), ColumnSpec.small("MAXIMUM_SCALE"), ColumnSpec.integer("SQL_DATA_TYPE"),
	    ColumnSpec.integer("SQL_DATETIME_SUB"), ColumnSpec.integer("NUM_PREC_RADIX"));

	@Override
	public ResultSet getTypeInfo() throws SQLException {
		Map<String, Object[]> byName = new LinkedHashMap<>();
		for (Map<String, Object> r : query(MetadataSql.types())) {
			String name = text(r.get("type_name"));
			if (name == null) {
				continue;
			}
			name = name.toUpperCase(Locale.ROOT);
			DuckTypes.Type t = DuckTypes.parse(text(r.get("logical_type")));
			boolean quoted = !t.isNumeric() && t.jdbcType() != Types.BOOLEAN && !t.isNested()
			                 && t.jdbcType() != Types.NULL;
			boolean decimal = t.jdbcType() == Types.DECIMAL;
			byName.putIfAbsent(name, new Object[] {name, t.jdbcType(), decimal ? (Integer) 38 : t.size(),
			    quoted ? "'" : null, quoted ? "'" : null, decimal ? "precision,scale" : null, (short) typeNullable,
			    t.jdbcType() == Types.VARCHAR, (short) typeSearchable, t.isUnsigned(), false, false, null,
			    (short) 0, decimal ? (short) 38 : (short) 0, null, null, t.isNumeric() ? 10 : null});
		}
		List<Object[]> rows = new ArrayList<>(byName.values());
		// JDBC: by DATA_TYPE, then how closely the type maps to it - the canonical name first
		rows.sort(Comparator.comparing((Object[] row) -> (Integer) row[1]).thenComparing(col(0)));
		return new RowsResultSet(TYPE_INFO_COLUMNS, rows);
	}

	// --- keys -----------------------------------------------------------------------------------

	static final List<ColumnSpec> PRIMARY_KEY_COLUMNS = List.of(ColumnSpec.text("TABLE_CAT"),
	    ColumnSpec.text("TABLE_SCHEM"), ColumnSpec.text("TABLE_NAME"), ColumnSpec.text("COLUMN_NAME"),
	    ColumnSpec.small("KEY_SEQ"), ColumnSpec.text("PK_NAME"));

	@Override
	public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
		List<Object[]> rows = new ArrayList<>();
		for (Map<String, Object> r : query(MetadataSql.primaryKeys(catalog, schema, table))) {
			String object = text(r.get("object"));
			String[] path = MetadataSql.splitPath(object);
			rows.add(new Object[] {r.get("vcat"), path[0], path[1], r.get("column"),
			    (short) intOrZero(r.get("key_sequence")), object + "_pk"});
		}
		return new RowsResultSet(PRIMARY_KEY_COLUMNS, rows);
	}

	static final List<ColumnSpec> KEY_COLUMNS = List.of(ColumnSpec.text("PKTABLE_CAT"), ColumnSpec.text("PKTABLE_SCHEM"),
	    ColumnSpec.text("PKTABLE_NAME"), ColumnSpec.text("PKCOLUMN_NAME"), ColumnSpec.text("FKTABLE_CAT"),
	    ColumnSpec.text("FKTABLE_SCHEM"), ColumnSpec.text("FKTABLE_NAME"), ColumnSpec.text("FKCOLUMN_NAME"),
	    ColumnSpec.small("KEY_SEQ"), ColumnSpec.small("UPDATE_RULE"), ColumnSpec.small("DELETE_RULE"),
	    ColumnSpec.text("FK_NAME"), ColumnSpec.text("PK_NAME"), ColumnSpec.small("DEFERRABILITY"));

	/** A reference as foreign key rows: one per column pair, KEY_SEQ from 1. */
	private List<Object[]> keyRows(String sql) throws SQLException {
		List<Object[]> rows = new ArrayList<>();
		for (Map<String, Object> r : query(sql)) {
			List<?> from = r.get("from_column_list") instanceof List<?> l ? l : List.of();
			List<?> to = r.get("to_column_list") instanceof List<?> l ? l : List.of();
			if (from.isEmpty() || from.size() != to.size()) {
				continue; // not column pairs: no foreign key to number
			}
			String[] fk = MetadataSql.splitPath(text(r.get("from_object")));
			String[] pk = MetadataSql.splitPath(text(r.get("to_object")));
			for (int i = 0; i < from.size(); i++) {
				rows.add(new Object[] {r.get("vcat"), pk[0], pk[1], text(to.get(i)), r.get("vcat"), fk[0], fk[1],
				    text(from.get(i)), (short) (i + 1), (short) importedKeyNoAction, (short) importedKeyNoAction,
				    r.get("name"), text(r.get("to_object")) + "_pk", (short) importedKeyNotDeferrable});
			}
		}
		return rows;
	}

	private static Comparator<Object[]> keyOrder(int catalog) {
		return col(catalog).thenComparing(col(catalog + 1)).thenComparing(col(catalog + 2)).thenComparing(col(11))
		    .thenComparing(row -> (Short) row[8]);
	}

	@Override
	public ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException {
		List<Object[]> rows = keyRows(MetadataSql.references(catalog, schema, table, null, null, null));
		rows.sort(keyOrder(0)); // by the referenced (primary key) table
		return new RowsResultSet(KEY_COLUMNS, rows);
	}

	@Override
	public ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException {
		List<Object[]> rows = keyRows(MetadataSql.references(null, null, null, catalog, schema, table));
		rows.sort(keyOrder(4)); // by the referencing (foreign key) table
		return new RowsResultSet(KEY_COLUMNS, rows);
	}

	@Override
	public ResultSet getCrossReference(String parentCatalog, String parentSchema, String parentTable,
	    String foreignCatalog, String foreignSchema, String foreignTable) throws SQLException {
		List<Object[]> rows = keyRows(
		    MetadataSql.references(foreignCatalog, foreignSchema, foreignTable, parentCatalog, parentSchema, parentTable));
		rows.sort(keyOrder(4));
		return new RowsResultSet(KEY_COLUMNS, rows);
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		return iface.isInstance(this) ? iface.cast(this) : delegate.unwrap(iface);
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return iface.isInstance(this) || delegate.isWrapperFor(iface);
	}

	// --- helpers --------------------------------------------------------------------------------

	static String specificName(String name, Object parameterTypes) {
		List<String> types = new ArrayList<>();
		if (parameterTypes instanceof List<?> list) {
			for (Object t : list) {
				types.add(String.valueOf(t));
			}
		}
		return name + "(" + String.join(", ", types) + ")";
	}

	private static Object remarks(Map<String, Object> r) {
		Object comment = r.get("comment");
		return comment != null ? comment : r.get("description");
	}

	private static boolean returnsTable(Object functionType) {
		String t = text(functionType);
		return "table".equals(t) || "table_macro".equals(t);
	}

	/** A JDBC LIKE pattern ({@code %}, {@code _}, {@code \} escape) as a regex. */
	static Pattern likePattern(String like) {
		StringBuilder re = new StringBuilder();
		for (int i = 0; i < like.length(); i++) {
			char c = like.charAt(i);
			if (c == '\\' && i + 1 < like.length()) {
				re.append(Pattern.quote(String.valueOf(like.charAt(++i))));
			} else if (c == '%') {
				re.append(".*");
			} else if (c == '_') {
				re.append('.');
			} else {
				re.append(Pattern.quote(String.valueOf(c)));
			}
		}
		return Pattern.compile(re.toString(), Pattern.DOTALL);
	}

	private static Integer nullableCode(Object isNullable) {
		if (isNullable == null) {
			return columnNullableUnknown;
		}
		return truthy(isNullable) ? columnNullable : columnNoNulls;
	}

	private static String isNullableText(Object isNullable) {
		if (isNullable == null) {
			return "";
		}
		return truthy(isNullable) ? "YES" : "NO";
	}

	static boolean truthy(Object v) {
		if (v instanceof Boolean b) {
			return b;
		}
		if (v instanceof Number n) {
			return n.intValue() != 0;
		}
		return v != null && (v.toString().equalsIgnoreCase("true") || v.toString().equalsIgnoreCase("yes"));
	}

	static String text(Object v) {
		return v == null ? null : v.toString();
	}

	private static Integer intOrNull(Object v) {
		return v instanceof Number n ? n.intValue() : v == null ? null : Integer.valueOf(v.toString().trim());
	}

	private static int intOrZero(Object v) {
		Integer i = intOrNull(v);
		return i == null ? 0 : i;
	}
}
