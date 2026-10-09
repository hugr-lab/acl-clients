package io.github.hugrlab.acl.jdbc;

import java.util.Locale;

/** The few texts the driver composes: the mode prefix, USE, quoted identifiers and literals. */
final class AclSql {
	private AclSql() {
	}

	/**
	 * The statement as the connection's mode sends it (spec 006): {@code data} unchanged, {@code manage}
	 * behind {@code ACL }, {@code native} behind {@code ACL NATIVE }. The node decides by the principal's
	 * scope; the driver only says which grammar is meant. Leading whitespace and comments go (the
	 * node's prefix scanner reads no comment); a text already written with an {@code ACL} prefix, and
	 * a {@code USE} in manage mode (the client's own session statement, not a management one), are sent
	 * as written.
	 */
	static String prefix(AclConfig.Mode mode, String sql) {
		if (sql == null || mode == AclConfig.Mode.DATA) {
			return sql;
		}
		String body = stripLeading(sql);
		if (startsWithWord(body, "ACL") || (mode == AclConfig.Mode.MANAGE && startsWithWord(body, "USE"))) {
			return body;
		}
		return (mode == AclConfig.Mode.MANAGE ? "ACL " : "ACL NATIVE ") + body;
	}

	/** True when the text (as the client wrote it, any prefix aside) changes the session's catalog. */
	static boolean isUse(String sql) {
		if (sql == null) {
			return false;
		}
		String body = stripLeading(sql);
		if (startsWithWord(body, "ACL")) {
			body = stripLeading(body.substring(3));
			if (startsWithWord(body, "NATIVE")) {
				body = stripLeading(body.substring(6));
			}
		}
		return startsWithWord(body, "USE");
	}

	/** duckdb's quoted identifier: double quotes, a quote inside doubled. */
	static String quoteIdentifier(String name) {
		return '"' + name.replace("\"", "\"\"") + '"';
	}

	/** A string literal, quotes doubled. */
	static String literal(String value) {
		return '\'' + value.replace("'", "''") + '\'';
	}

	/** A LIKE pattern matching {@code value} exactly ({@code \} as the escape, spec 115's SqlInfo). */
	static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	static String stripLeading(String sql) {
		int i = 0;
		int n = sql.length();
		while (i < n) {
			char c = sql.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
			} else if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
				int eol = sql.indexOf('\n', i);
				i = eol < 0 ? n : eol + 1;
			} else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
				int end = sql.indexOf("*/", i + 2);
				i = end < 0 ? n : end + 2;
			} else {
				break;
			}
		}
		return sql.substring(i);
	}

	static boolean startsWithWord(String body, String word) {
		if (!body.regionMatches(true, 0, word, 0, word.length())) {
			return false;
		}
		return body.length() == word.length() || !isWordChar(body.charAt(word.length()));
	}

	private static boolean isWordChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}

	static String upper(String s) {
		return s == null ? null : s.toUpperCase(Locale.ROOT);
	}
}
