// Adapted from duckdb-java (MIT, Copyright 2018-2025 Stichting DuckDB Foundation):
// DuckDBResultSetMetaData.type_to_int / type_to_javaString - the type table. See THIRD-PARTY.md.
package io.github.hugrlab.acl.jdbc;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One parser of duckdb's type text (what {@code duckdb_columns().data_type} and the field metadata
 * {@code ARROW:FLIGHT:SQL:TYPE_NAME} carry) for the metadata listings and the result sets alike. The
 * JDBC code follows duckdb-java's table, with its list bug fixed: {@code INTEGER[]} is an ARRAY (there,
 * the text never matched {@code LIST}).
 */
final class DuckTypes {
	private DuckTypes() {
	}

	/** A field of a STRUCT: its name as declared and its type. */
	record Field(String name, Type type) {
	}

	/**
	 * A parsed type. {@code base} is the canonical upper-case name ({@code STRUCT}, {@code LIST},
	 * {@code ARRAY}, {@code DECIMAL}, {@code INTEGER}, ...); {@code size} / {@code digits} are JDBC's
	 * COLUMN_SIZE / DECIMAL_DIGITS (null where they do not apply).
	 */
	record Type(String text, String base, int jdbcType, Integer size, Integer digits, List<Field> fields, Type element,
	    Type key, Type value) {
		boolean isStruct() {
			return "STRUCT".equals(base);
		}

		boolean isList() {
			return "LIST".equals(base) || "ARRAY".equals(base);
		}

		boolean isMap() {
			return "MAP".equals(base);
		}

		boolean isNested() {
			return isStruct() || isList() || isMap() || "UNION".equals(base);
		}

		boolean isNumeric() {
			return switch (jdbcType) {
				case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.NUMERIC, Types.DECIMAL,
				     Types.FLOAT, Types.REAL, Types.DOUBLE -> true;
				default -> false;
			};
		}

		boolean isUnsigned() {
			return base.startsWith("U") && !"UUID".equals(base) && !"UNION".equals(base);
		}
	}

	static Type parse(String text) {
		if (text == null || text.isBlank()) {
			return scalar("", "UNKNOWN", Types.OTHER, null, null);
		}
		String t = text.trim();
		// LIST (`T[]`) and ARRAY (`T[n]`): the outermost trailing brackets
		if (t.endsWith("]")) {
			int open = matchingOpen(t, t.length() - 1, '[', ']');
			if (open > 0) {
				Type element = parse(t.substring(0, open));
				String inside = t.substring(open + 1, t.length() - 1).trim();
				return new Type(t, inside.isEmpty() ? "LIST" : "ARRAY", Types.ARRAY, null, null, List.of(), element,
				    null, null);
			}
		}
		String upper = t.toUpperCase(Locale.ROOT);
		int paren = t.indexOf('(');
		String head = (paren < 0 ? upper : upper.substring(0, paren)).trim();
		String args = paren < 0 || !t.endsWith(")") ? null : t.substring(paren + 1, t.length() - 1);
		switch (head) {
			case "STRUCT", "ROW": {
				List<Field> fields = new ArrayList<>();
				if (args != null) {
					for (String part : splitTop(args)) {
						String[] nameType = fieldNameAndType(part.trim());
						fields.add(new Field(nameType[0], parse(nameType[1])));
					}
				}
				return new Type(t, "STRUCT", Types.STRUCT, null, null, List.copyOf(fields), null, null, null);
			}
			case "MAP": {
				Type key = null;
				Type value = null;
				if (args != null) {
					List<String> kv = splitTop(args);
					if (kv.size() == 2) {
						key = parse(kv.get(0));
						value = parse(kv.get(1));
					}
				}
				return new Type(t, "MAP", Types.OTHER, null, null, List.of(), null, key, value);
			}
			case "LIST":
				return new Type(t, "LIST", Types.ARRAY, null, null, List.of(), args == null ? null : parse(args), null,
				    null);
			case "ARRAY":
				return new Type(t, "ARRAY", Types.ARRAY, null, null, List.of(), null, null, null);
			case "UNION":
				return scalar(t, "UNION", Types.OTHER, null, null);
			case "ENUM":
				return scalar(t, "ENUM", Types.VARCHAR, null, null); // a dictionary of utf8 on the wire
			case "DECIMAL", "NUMERIC": {
				int precision = 18;
				int scale = 3;
				if (args != null) {
					List<String> ps = splitTop(args);
					precision = parseIntOr(ps.get(0), 18);
					scale = ps.size() > 1 ? parseIntOr(ps.get(1), 0) : 0;
				}
				return scalar(t, "DECIMAL", Types.DECIMAL, precision, scale);
			}
			case "VARCHAR", "CHAR", "BPCHAR", "TEXT", "STRING", "NVARCHAR":
				return scalar(t, "VARCHAR", Types.VARCHAR, args == null ? null : parseIntOr(args, null), null);
			default:
				return named(t, head);
		}
	}

	private static Type named(String text, String name) {
		return switch (name) {
			case "BOOLEAN", "BOOL", "LOGICAL" -> scalar(text, "BOOLEAN", Types.BOOLEAN, 1, null);
			case "TINYINT", "INT1" -> scalar(text, "TINYINT", Types.TINYINT, 3, 0);
			case "UTINYINT" -> scalar(text, "UTINYINT", Types.SMALLINT, 3, 0);
			case "SMALLINT", "INT2", "SHORT" -> scalar(text, "SMALLINT", Types.SMALLINT, 5, 0);
			case "USMALLINT" -> scalar(text, "USMALLINT", Types.INTEGER, 5, 0);
			case "INTEGER", "INT", "INT4", "SIGNED" -> scalar(text, "INTEGER", Types.INTEGER, 10, 0);
			case "UINTEGER" -> scalar(text, "UINTEGER", Types.BIGINT, 10, 0);
			case "BIGINT", "INT8", "LONG" -> scalar(text, "BIGINT", Types.BIGINT, 19, 0);
			case "UBIGINT" -> scalar(text, "UBIGINT", Types.NUMERIC, 20, 0);
			case "HUGEINT", "INT128" -> scalar(text, "HUGEINT", Types.NUMERIC, 39, 0);
			case "UHUGEINT" -> scalar(text, "UHUGEINT", Types.NUMERIC, 39, 0);
			case "BIGNUM", "VARINT" -> scalar(text, "BIGNUM", Types.NUMERIC, null, 0);
			case "FLOAT", "FLOAT4", "REAL" -> scalar(text, "FLOAT", Types.FLOAT, 7, null);
			case "DOUBLE", "FLOAT8" -> scalar(text, "DOUBLE", Types.DOUBLE, 15, null);
			case "DATE" -> scalar(text, "DATE", Types.DATE, 10, null);
			case "TIME", "TIME_NS" -> scalar(text, name, Types.TIME, 15, 6);
			case "TIME WITH TIME ZONE", "TIMETZ" -> scalar(text, "TIME WITH TIME ZONE", Types.TIME_WITH_TIMEZONE, 21, 6);
			case "TIMESTAMP", "DATETIME", "TIMESTAMP_US", "TIMESTAMP_S", "TIMESTAMP_MS", "TIMESTAMP_NS" ->
			    scalar(text, name, Types.TIMESTAMP, 26, 6);
			case "TIMESTAMP WITH TIME ZONE", "TIMESTAMPTZ", "TIMESTAMPTZ_NS" ->
			    scalar(text, "TIMESTAMP WITH TIME ZONE", Types.TIMESTAMP_WITH_TIMEZONE, 32, 6);
			case "INTERVAL" -> scalar(text, "INTERVAL", Types.OTHER, null, null);
			case "BLOB", "BYTEA", "BINARY", "VARBINARY", "GEOMETRY" -> scalar(text, name, Types.BLOB, null, null);
			case "BIT", "BITSTRING" -> scalar(text, "BIT", Types.BIT, null, null);
			case "UUID" -> scalar(text, "UUID", Types.OTHER, 36, null);
			case "JSON" -> scalar(text, "JSON", Types.VARCHAR, null, null);
			case "NULL" -> scalar(text, "NULL", Types.NULL, null, null);
			case "STRUCT", "ROW" -> new Type(text, "STRUCT", Types.STRUCT, null, null, List.of(), null, null, null);
			default -> scalar(text, name, Types.OTHER, null, null);
		};
	}

	private static Type scalar(String text, String base, int jdbc, Integer size, Integer digits) {
		return new Type(text, base, jdbc, size, digits, List.of(), null, null, null);
	}

	/**
	 * The class {@code getObject} returns for a scalar of this type through Arrow's driver (duckdb-java's
	 * table, adjusted to the objects Arrow's accessors build), or null when unknown.
	 */
	static String javaClass(Type type) {
		if (type.isUnsigned()) {
			return null; // Arrow's unsigned accessors: not ours to guess
		}
		return switch (type.jdbcType()) {
			case Types.BOOLEAN -> Boolean.class.getName();
			case Types.TINYINT -> Byte.class.getName();
			case Types.SMALLINT -> Short.class.getName();
			case Types.INTEGER -> Integer.class.getName();
			case Types.BIGINT -> Long.class.getName();
			case Types.NUMERIC, Types.DECIMAL -> java.math.BigDecimal.class.getName();
			case Types.FLOAT, Types.REAL -> Float.class.getName();
			case Types.DOUBLE -> Double.class.getName();
			case Types.VARCHAR -> String.class.getName();
			case Types.DATE -> java.sql.Date.class.getName();
			case Types.TIME -> java.sql.Time.class.getName();
			case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> java.sql.Timestamp.class.getName();
			case Types.BLOB -> byte[].class.getName();
			default -> null;
		};
	}

	/** The JDBC type name of a code, for a column whose own type text is unknown. */
	static String jdbcName(int jdbcType) {
		for (java.lang.reflect.Field f : Types.class.getFields()) {
			try {
				if (f.getInt(null) == jdbcType) {
					return f.getName();
				}
			} catch (IllegalAccessException e) {
				// public constants
			}
		}
		return "OTHER";
	}

	/** {@code name TYPE} of a STRUCT field; the name may be double-quoted with {@code ""} doubling. */
	static String[] fieldNameAndType(String part) {
		if (part.startsWith("\"")) {
			StringBuilder name = new StringBuilder();
			int i = 1;
			while (i < part.length()) {
				char c = part.charAt(i);
				if (c == '"') {
					if (i + 1 < part.length() && part.charAt(i + 1) == '"') {
						name.append('"');
						i += 2;
						continue;
					}
					i++;
					break;
				}
				name.append(c);
				i++;
			}
			return new String[] {name.toString(), part.substring(i).trim()};
		}
		int space = part.indexOf(' ');
		return space < 0 ? new String[] {part, ""} : new String[] {part.substring(0, space), part.substring(space + 1).trim()};
	}

	/** Splits at commas outside parentheses, brackets and quotes. */
	static List<String> splitTop(String s) {
		List<String> out = new ArrayList<>();
		int depth = 0;
		char quote = 0;
		int start = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (quote != 0) {
				if (c == quote) {
					quote = 0;
				}
				continue;
			}
			switch (c) {
				case '"', '\'' -> quote = c;
				case '(', '[' -> depth++;
				case ')', ']' -> depth--;
				case ',' -> {
					if (depth == 0) {
						out.add(s.substring(start, i).trim());
						start = i + 1;
					}
				}
				default -> {
				}
			}
		}
		String last = s.substring(start).trim();
		if (!last.isEmpty() || !out.isEmpty()) {
			out.add(last);
		}
		return out;
	}

	private static int matchingOpen(String s, int close, char open, char shut) {
		int depth = 0;
		char quote = 0;
		for (int i = close; i >= 0; i--) {
			char c = s.charAt(i);
			if (quote != 0) {
				if (c == quote) {
					quote = 0;
				}
				continue;
			}
			if (c == '"' || c == '\'') {
				quote = c;
			} else if (c == shut) {
				depth++;
			} else if (c == open) {
				depth--;
				if (depth == 0) {
					return i;
				}
			}
		}
		return -1;
	}

	private static Integer parseIntOr(String s, Integer fallback) {
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
