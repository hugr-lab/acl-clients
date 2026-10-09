package io.github.hugrlab.acl.jdbc;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 * A callable statement of an {@link AclConnection}: its text was prefixed by the connection's mode
 * when it was prepared (spec 006); here a {@code USE} is noticed at each execution and the results
 * are wrapped in {@link AclResultSet}.
 */
final class AclCallableStatement extends ForwardingCallableStatement<CallableStatement> {
	private final AclConnection connection;
	private final String sql;

	AclCallableStatement(CallableStatement arrow, AclConnection connection, String sql) {
		super(arrow);
		this.connection = connection;
		this.sql = sql;
	}

	private ResultSet wrap(ResultSet rs) {
		return AclResultSet.wrap(rs, this, connection.nestedJson());
	}

	@Override
	public ResultSet executeQuery() throws SQLException {
		connection.noteStatement(sql);
		return wrap(delegate.executeQuery());
	}

	@Override
	public boolean execute() throws SQLException {
		connection.noteStatement(sql);
		return delegate.execute();
	}

	@Override
	public int executeUpdate() throws SQLException {
		connection.noteStatement(sql);
		return delegate.executeUpdate();
	}

	@Override
	public long executeLargeUpdate() throws SQLException {
		connection.noteStatement(sql);
		return delegate.executeLargeUpdate();
	}

	@Override
	public int[] executeBatch() throws SQLException {
		connection.noteStatement(sql);
		return delegate.executeBatch();
	}

	@Override
	public ResultSet getResultSet() throws SQLException {
		return wrap(delegate.getResultSet());
	}

	@Override
	public ResultSet getGeneratedKeys() throws SQLException {
		return wrap(delegate.getGeneratedKeys());
	}

	@Override
	public ResultSetMetaData getMetaData() throws SQLException {
		ResultSetMetaData arrow = delegate.getMetaData();
		return arrow == null ? null
		                     : new AclResultSetMetaData(arrow, ResultTypes.of(delegate, arrow.getColumnCount()),
		                         connection.nestedJson());
	}

	@Override
	public Connection getConnection() {
		return connection;
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
