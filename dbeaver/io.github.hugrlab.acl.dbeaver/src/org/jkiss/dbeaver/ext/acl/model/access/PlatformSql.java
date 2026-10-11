package org.jkiss.dbeaver.ext.acl.model.access;

/**
 * The statements the Access control branch reads (spec 007.3): one `platform` view or read function each, narrowed to
 * what is expanded - filters as literals (a quote doubled), names as duckdb-quoted identifiers. Never the policy
 * store's own tables.
 */
public final class PlatformSql {
	private PlatformSql() {
	}

	public static String literal(String value) {
		return "'" + value.replace("'", "''") + "'";
	}

	public static String identifier(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}

	/** `SELECT * FROM platform."view" WHERE "column" = 'value' AND …` - pairs of column, value. */
	public static String view(String view, String... pairs) {
		StringBuilder sql = new StringBuilder("SELECT * FROM platform.").append(identifier(view));
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			sql.append(i == 0 ? " WHERE " : " AND ").append(identifier(pairs[i])).append(" = ").append(literal(pairs[i + 1]));
		}
		return sql.toString();
	}

	/** `SELECT * FROM platform."function"('a', 'b')` - a read function in FROM (spec 117 / 120.2). */
	public static String call(String function, String... arguments) {
		StringBuilder sql = new StringBuilder("SELECT * FROM platform.").append(identifier(function)).append('(');
		for (int i = 0; i < arguments.length; i++) {
			sql.append(i == 0 ? "" : ", ").append(literal(arguments[i]));
		}
		return sql.append(')').toString();
	}
}
