package org.jkiss.dbeaver.ext.acl.model.access;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * What a management statement answered (duckdb-acl spec 179, this repository's spec 010): one row, {@code ok} and
 * {@code message} - a false says what did not happen, a message what the node has to say beside a write (a count, a
 * grant's hint naming the groups whose source set leaves out a source the role now reads, with the repair to run).
 * A refusal is an error, never an answer. A form that answers rows (CHECK) has no {@code ok} column and no answer.
 */
public record AclAnswer(boolean ok, String message) {
	/** The answer of the result's first row, or null when the result is not a management answer. */
	public static AclAnswer read(ResultSet rows) throws SQLException {
		ResultSetMetaData meta = rows.getMetaData();
		List<String> columns = new ArrayList<>();
		for (int i = 1; i <= meta.getColumnCount(); i++) {
			columns.add(meta.getColumnLabel(i));
		}
		if (!rows.next()) {
			return null;
		}
		int ok = columns.indexOf("ok");
		int message = columns.indexOf("message");
		return of(columns, ok < 0 ? null : rows.getObject(ok + 1), message < 0 ? null : rows.getObject(message + 1));
	}

	/**
	 * The answer from a row's {@code ok} and {@code message} values: none without both columns; an {@code ok} that is
	 * NULL or no boolean is not ok (the node never answers one, so the client does not read it as success).
	 */
	public static AclAnswer of(List<String> columns, Object ok, Object message) {
		if (!columns.contains("ok") || !columns.contains("message")) {
			return null;
		}
		return new AclAnswer(Boolean.TRUE.equals(ok), message == null ? "" : String.valueOf(message));
	}

	/** Worth showing: a false, or anything the node said. */
	public boolean says() {
		return !ok || !message.isEmpty();
	}

	/**
	 * What a run of statements answered, as the user reads it: each statement that said something, with its message
	 * (a false marked {@code not done}); empty when nothing was said.
	 */
	public static String report(List<String> statements, List<AclAnswer> answers) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < statements.size() && i < answers.size(); i++) {
			AclAnswer answer = answers.get(i);
			if (answer == null || !answer.says()) {
				continue;
			}
			if (out.length() > 0) {
				out.append("\n\n");
			}
			out.append(statements.get(i)).append("\n  ").append(answer.ok() ? "" : "not done: ")
			    .append(answer.message().isEmpty() ? "the node answered nothing more" : answer.message());
		}
		return out.toString();
	}

	/** Every answer ok (a statement that answered none - not a management form - counts as done). */
	public static boolean allOk(List<AclAnswer> answers) {
		for (AclAnswer answer : answers) {
			if (answer != null && !answer.ok()) {
				return false;
			}
		}
		return true;
	}
}
