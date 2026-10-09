package io.github.hugrlab.acl.jdbc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The few texts the driver composes: the mode prefix, USE, quoted identifiers and literals. */
final class AclSql {
	private AclSql() {
	}

	/**
	 * The statement as the connection's mode sends it (spec 006): {@code data} unchanged, {@code manage}
	 * behind {@code ACL }, {@code native} behind {@code ACL NATIVE }. The node decides by the principal's
	 * scope; the driver only says which grammar is meant.
	 *
	 * <p>Sent as written: a text whose first statement already starts with {@code ACL}, and a text that
	 * is ONE {@code USE} statement - the session's catalog is virtual in every mode, like the tree and
	 * {@code setCatalog} (a physical USE is written {@code ACL NATIVE USE ...}). The prefix goes in front
	 * of the text as written, comments included, so nothing commented out can surface; the one
	 * exception is a management statement, whose leading comments are dropped (the node's management
	 * grammar reads none - only whole comments, found the way duckdb's lexer finds them: {@code --} to
	 * the end of the line, block comments nested). A text with no statement at all is still prefixed,
	 * never dropped.
	 */
	static String prefix(AclConfig.Mode mode, String sql) {
		if (sql == null || mode == AclConfig.Mode.DATA) {
			return sql;
		}
		int first = skipSpace(sql, 0);
		if (first < 0 || first >= sql.length()) {
			return (mode == AclConfig.Mode.MANAGE ? "ACL " : "ACL NATIVE ") + sql; // nothing to read: the node says so
		}
		if (startsWithWord(sql, first, "ACL") || isSingleUse(sql)) {
			return sql;
		}
		return mode == AclConfig.Mode.MANAGE ? "ACL " + sql.substring(first) : "ACL NATIVE " + sql;
	}

	/** True when the text is exactly one statement and that statement is a {@code USE}. */
	static boolean isSingleUse(String sql) {
		List<Integer> starts = statementStarts(sql);
		return starts.size() == 1 && startsWithWord(sql, starts.get(0), "USE");
	}

	/**
	 * True when any statement of the text (as the client wrote it, an {@code ACL [NATIVE]} marker aside)
	 * is a {@code USE}: it changes the session's catalog.
	 */
	static boolean containsUse(String sql) {
		if (sql == null) {
			return false;
		}
		for (int start : statementStarts(sql)) {
			int i = start;
			if (startsWithWord(sql, i, "ACL")) {
				i = skipSpace(sql, i + 3);
				if (i >= 0 && startsWithWord(sql, i, "NATIVE")) {
					i = skipSpace(sql, i + 6);
				}
			}
			if (i >= 0 && startsWithWord(sql, i, "USE")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Where each statement's first token is: after the start and after each {@code ;} outside quotes and
	 * comments. A statement that is only whitespace and comments is none; text after an unterminated
	 * comment or quote belongs to the statement it is in.
	 */
	static List<Integer> statementStarts(String sql) {
		List<Integer> out = new ArrayList<>();
		int n = sql.length();
		int i = 0;
		while (i < n) {
			int start = skipSpace(sql, i);
			if (start < 0 || start >= n) {
				break;
			}
			if (sql.charAt(start) != ';') {
				out.add(start);
			}
			i = endOfStatement(sql, start);
			if (i < n) {
				i++; // past the ';'
			}
		}
		return out;
	}

	/** The index of the {@code ;} that ends the statement starting at {@code i}, or the text's length. */
	private static int endOfStatement(String sql, int i) {
		int n = sql.length();
		while (i < n) {
			char c = sql.charAt(i);
			if (c == ';') {
				return i;
			}
			if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-' || c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
				int next = skipSpace(sql, i);
				if (next < 0) {
					return n;
				}
				i = next;
			} else if (c == '\'') {
				boolean escapes = i > 0 && (sql.charAt(i - 1) == 'e' || sql.charAt(i - 1) == 'E')
				                  && (i < 2 || !isWordChar(sql.charAt(i - 2)));
				i = endOfQuoted(sql, i, '\'', escapes);
			} else if (c == '"') {
				i = endOfQuoted(sql, i, '"', false);
			} else if (c == '$') {
				i = endOfDollar(sql, i);
			} else {
				i++;
			}
		}
		return n;
	}

	/** Past the closing quote ({@code ''} doubled; {@code \} escapes in an E-string), or the length. */
	private static int endOfQuoted(String sql, int open, char quote, boolean backslash) {
		int n = sql.length();
		int i = open + 1;
		while (i < n) {
			char c = sql.charAt(i);
			if (backslash && c == '\\') {
				i += 2;
			} else if (c == quote) {
				if (i + 1 < n && sql.charAt(i + 1) == quote) {
					i += 2;
				} else {
					return i + 1;
				}
			} else {
				i++;
			}
		}
		return n;
	}

	/** A dollar-quoted string ({@code $$...$$}, {@code $tag$...$tag$}); a parameter {@code $1} is one char. */
	private static int endOfDollar(String sql, int open) {
		int n = sql.length();
		int i = open + 1;
		if (i < n && (Character.isLetter(sql.charAt(i)) || sql.charAt(i) == '_')) {
			while (i < n && isWordChar(sql.charAt(i))) {
				i++;
			}
		}
		if (i >= n || sql.charAt(i) != '$') {
			return open + 1;
		}
		String tag = sql.substring(open, i + 1);
		int close = sql.indexOf(tag, i + 1);
		return close < 0 ? n : close + tag.length();
	}

	/**
	 * The first index at or after {@code i} that is not whitespace or a comment, as duckdb's lexer skips
	 * them: {@code --} ends at {@code \n} or {@code \r}, block comments nest. The length when only
	 * those remain, -1 when a block comment never closes (everything after it is comment).
	 */
	static int skipSpace(String sql, int i) {
		int n = sql.length();
		while (i < n) {
			char c = sql.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
			} else if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
				i += 2;
				while (i < n && sql.charAt(i) != '\n' && sql.charAt(i) != '\r') {
					i++;
				}
			} else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
				int depth = 1;
				i += 2;
				while (i < n && depth > 0) {
					if (sql.charAt(i) == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
						depth++;
						i += 2;
					} else if (sql.charAt(i) == '*' && i + 1 < n && sql.charAt(i + 1) == '/') {
						depth--;
						i += 2;
					} else {
						i++;
					}
				}
				if (depth > 0) {
					return -1;
				}
			} else {
				break;
			}
		}
		return i;
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

	/**
	 * A tool's LIKE pattern as the node can run it: a trailing {@code \} that escapes nothing is a
	 * literal backslash (duckdb refuses a pattern ending in its escape character).
	 */
	static String likePattern(String pattern) {
		int i = 0;
		int n = pattern.length();
		while (i < n) {
			if (pattern.charAt(i) == '\\') {
				if (i + 1 == n) {
					return pattern + '\\';
				}
				i += 2;
			} else {
				i++;
			}
		}
		return pattern;
	}

	static boolean startsWithWord(String sql, int at, String word) {
		if (at < 0 || !sql.regionMatches(true, at, word, 0, word.length())) {
			return false;
		}
		int end = at + word.length();
		return sql.length() == end || !isWordChar(sql.charAt(end));
	}

	static boolean startsWithWord(String body, String word) {
		return startsWithWord(body, 0, word);
	}

	private static boolean isWordChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}

	static String upper(String s) {
		return s == null ? null : s.toUpperCase(Locale.ROOT);
	}
}
