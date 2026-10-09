package io.github.hugrlab.acl.jdbc;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The connection a client holds: Arrow's Flight SQL connection, with what the door needs said
 * differently (spec 006).
 *
 * <ul>
 * <li>{@code setCatalog} / {@code setSchema} are {@code USE <catalog>} / {@code USE SCHEMA <schema>}
 * (duckdb-acl spec 114) - Arrow sends Flight {@code SetSessionOptions}, which the door refuses;
 * {@code getCatalog} / {@code getSchema} read {@code current_database()} / {@code current_schema()},
 * kept until a {@code USE} through any statement of this connection.
 * <li>{@code acl.mode} sends every statement as data, management ({@code ACL }) or native
 * ({@code ACL NATIVE }) SQL. The metadata listings are never prefixed: the tree is always the
 * principal's virtual catalog.
 * <li>results carry duckdb's types ({@link AclResultSet}).
 * </ul>
 */
final class AclConnection extends ForwardingConnection {
	private final AclConfig.Mode mode;
	private final boolean nestedJson;
	private volatile boolean warmed;
	private volatile String[] current; // [catalog, schema], read once per USE

	AclConnection(Connection arrow, AclConfig config) throws SQLException {
		this(arrow, config.mode, config.nested == AclConfig.Nested.JSON);
		try {
			if (config.catalog != null) {
				setCatalog(config.catalog);
			}
			if (config.schema != null) {
				setSchema(config.schema);
			}
		} catch (SQLException e) {
			try {
				arrow.close();
			} catch (SQLException closing) {
				e.addSuppressed(closing);
			}
			throw e;
		}
	}

	AclConnection(Connection arrow, AclConfig.Mode mode, boolean nestedJson) {
		super(arrow);
		this.mode = mode;
		this.nestedJson = nestedJson;
	}

	boolean nestedJson() {
		return nestedJson;
	}

	AclConfig.Mode mode() {
		return mode;
	}

	String prefix(String sql) {
		return AclSql.prefix(mode, sql);
	}

	/** A call that may not go out yet: a statement, a prepare, a listing. */
	interface Call<T> {
		T run() throws SQLException;
	}

	/**
	 * Runs one call of a client's statement: a USE must land in the session the door identifies by its
	 * cookie, so if nothing has earned the cookie yet a cheap call goes first (see {@link #run(String)});
	 * a call that succeeded means the session exists; a text with a USE - whether it succeeded or not, a
	 * batch may have run part of it - makes {@code getCatalog} read again once it is done.
	 */
	<T> T call(boolean use, Call<T> call) throws SQLException {
		if (use && !warmed) {
			warm();
		}
		boolean ok = false;
		try {
			T result = call.run();
			ok = true;
			return result;
		} finally {
			if (ok) {
				warmed = true;
			}
			if (use) {
				current = null;
			}
		}
	}

	/** Arrow's connection: what the metadata listings run on, never prefixed. */
	Connection arrow() {
		return delegate;
	}

	/**
	 * Runs one of the driver's own statements on Arrow's connection, unprefixed. The door identifies a
	 * client's session by a cookie it hands out on the first call; a USE sent as the first call of a
	 * connection would go to a per-call session and be lost (duckdb-acl spec 050), so a cheap call
	 * earns the cookie first.
	 */
	private void run(String sql) throws SQLException {
		call(true, () -> {
			try (Statement s = delegate.createStatement()) {
				return s.execute(sql);
			}
		});
	}

	/** The cookie's first call: only once one has succeeded is the session there for a USE. */
	private void warm() throws SQLException {
		try (Statement s = delegate.createStatement(); ResultSet rs = s.executeQuery("SELECT 1")) {
			while (rs.next()) {
				// drained
			}
		}
		warmed = true;
	}

	@Override
	public void setCatalog(String catalog) throws SQLException {
		if (catalog == null || catalog.isEmpty()) {
			return; // JDBC: a driver that does not support the name ignores it
		}
		run("USE " + AclSql.quoteIdentifier(catalog));
	}

	@Override
	public void setSchema(String schema) throws SQLException {
		if (schema == null || schema.isEmpty()) {
			return;
		}
		// a nested schema is its dotted path as ONE name: the node reads USE SCHEMA "raw.eu" as that path
		run("USE SCHEMA " + AclSql.quoteIdentifier(schema));
	}

	private String[] current() throws SQLException {
		String[] now = current;
		if (now == null) {
			try (Statement s = delegate.createStatement();
			     ResultSet rs = s.executeQuery("SELECT current_database(), current_schema()")) {
				now = rs.next() ? new String[] {rs.getString(1), rs.getString(2)} : new String[2];
			}
			warmed = true;
			current = now;
		}
		return now;
	}

	@Override
	public String getCatalog() throws SQLException {
		return current()[0];
	}

	@Override
	public String getSchema() throws SQLException {
		return current()[1];
	}

	@Override
	public DatabaseMetaData getMetaData() throws SQLException {
		return new AclDatabaseMetaData(this, delegate.getMetaData());
	}

	@Override
	public String nativeSQL(String sql) throws SQLException {
		return delegate.nativeSQL(prefix(sql));
	}

	@Override
	public Statement createStatement() throws SQLException {
		return new AclStatement(delegate.createStatement(), this);
	}

	@Override
	public Statement createStatement(int type, int concurrency) throws SQLException {
		return new AclStatement(delegate.createStatement(type, concurrency), this);
	}

	@Override
	public Statement createStatement(int type, int concurrency, int holdability) throws SQLException {
		return new AclStatement(delegate.createStatement(type, concurrency, holdability), this);
	}

	/** A prepare is a call too: one of a USE waits for the cookie (it is not the USE itself). */
	private <T> T prepare(String sql, Call<T> prepare) throws SQLException {
		if (!warmed && AclSql.containsUse(sql)) {
			warm();
		}
		return call(false, prepare);
	}

	@Override
	public PreparedStatement prepareStatement(String sql) throws SQLException {
		return new AclPreparedStatement(prepare(sql, () -> delegate.prepareStatement(prefix(sql))), this, sql);
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
		return new AclPreparedStatement(
		    prepare(sql, () -> delegate.prepareStatement(prefix(sql), autoGeneratedKeys)), this, sql);
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
		return new AclPreparedStatement(
		    prepare(sql, () -> delegate.prepareStatement(prefix(sql), columnIndexes)), this, sql);
	}

	@Override
	public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
		return new AclPreparedStatement(
		    prepare(sql, () -> delegate.prepareStatement(prefix(sql), columnNames)), this, sql);
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int type, int concurrency) throws SQLException {
		return new AclPreparedStatement(
		    prepare(sql, () -> delegate.prepareStatement(prefix(sql), type, concurrency)), this, sql);
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int type, int concurrency, int holdability)
	    throws SQLException {
		return new AclPreparedStatement(
		    prepare(sql, () -> delegate.prepareStatement(prefix(sql), type, concurrency, holdability)), this, sql);
	}

	@Override
	public CallableStatement prepareCall(String sql) throws SQLException {
		return new AclCallableStatement(prepare(sql, () -> delegate.prepareCall(prefix(sql))), this, sql);
	}

	@Override
	public CallableStatement prepareCall(String sql, int type, int concurrency) throws SQLException {
		return new AclCallableStatement(
		    prepare(sql, () -> delegate.prepareCall(prefix(sql), type, concurrency)), this, sql);
	}

	@Override
	public CallableStatement prepareCall(String sql, int type, int concurrency, int holdability)
	    throws SQLException {
		return new AclCallableStatement(
		    prepare(sql, () -> delegate.prepareCall(prefix(sql), type, concurrency, holdability)), this, sql);
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		return iface.isInstance(this) ? iface.cast(this) : delegate.unwrap(iface);
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return iface.isInstance(this) || delegate.isWrapperFor(iface);
	}
}
