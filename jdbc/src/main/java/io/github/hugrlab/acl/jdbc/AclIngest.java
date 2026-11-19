package io.github.hugrlab.acl.jdbc;

import java.io.InputStream;
import java.sql.SQLException;

/**
 * Flight SQL bulk ingest through the driver's connection (spec 009): JDBC has no call for it, so a client reaches it
 * with {@code connection.unwrap(AclIngest.class)}. The rows are an Arrow IPC stream - the format, not a Java type, so a
 * caller's own Arrow version never meets the driver's shaded one. The load runs on the connection's session, with its
 * token and headers, and in a transaction of its own: the node refuses it while the connection has one open (autocommit
 * off with work pending).
 *
 * <p>{@link Mode#MERGE} and {@link Mode#REMOVE} are the node's loads by key (duckdb-acl spec 173): the stream's rows
 * matched to the table's declared {@code PRIMARY KEY}, sent as the option {@code acl.mode}; they need the
 * {@code ingest} capability with {@code merge} / {@code delete}, as every load needs {@code ingest} on top of its verb.
 */
public interface AclIngest {
	/** What the load does with the target (Flight SQL's {@code TableDefinitionOptions}, and the node's by-key modes). */
	enum Mode {
		/** Appends to an existing table. */
		APPEND,
		/** Creates the table; refused when it exists. */
		CREATE,
		/** Replaces the table, or creates it. */
		REPLACE,
		/** Updates the rows whose key the stream carries and inserts the rest; a key given twice - its last row. */
		MERGE,
		/** Deletes the rows whose key the stream carries; its other columns are not read. */
		REMOVE
	}

	/**
	 * Loads an Arrow IPC stream into {@code table}.
	 *
	 * @param catalog the virtual catalog, or {@code null} for the session's
	 * @param schema the schema, as one name, or {@code null} for the session's
	 * @param table the table's name, as one name
	 * @return the rows the node reports: written, or for {@code MERGE} / {@code REMOVE} the rows affected
	 */
	long ingest(String catalog, String schema, String table, InputStream arrowStream, Mode mode) throws SQLException;

	/** {@link #ingest(String, String, String, InputStream, Mode)} into the session's catalog and schema. */
	default long ingest(String table, InputStream arrowStream, Mode mode) throws SQLException {
		return ingest(null, null, table, arrowStream, mode);
	}
}
