package io.github.hugrlab.acl.jdbc;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A statement's result through Arrow's driver, with duckdb's types (spec 006): the metadata of
 * {@link AclResultSetMetaData}, and nested values as JDBC objects - a STRUCT as {@link AclStruct}, a
 * MAP as a {@code Map}, a LIST as a {@code java.sql.Array} - or, with {@code nested=json}, as JSON text.
 */
final class AclResultSet extends ForwardingResultSet {
	private final Statement statement;
	private final boolean nestedJson;
	private AclResultSetMetaData meta;

	AclResultSet(ResultSet arrow, Statement statement, boolean nestedJson) {
		super(arrow);
		this.statement = statement;
		this.nestedJson = nestedJson;
	}

	static ResultSet wrap(ResultSet arrow, Statement statement, boolean nestedJson) {
		return arrow == null || arrow instanceof AclResultSet ? arrow : new AclResultSet(arrow, statement, nestedJson);
	}

	private AclResultSetMetaData meta() throws SQLException {
		if (meta == null) {
			ResultSetMetaData arrow = delegate.getMetaData();
			meta = new AclResultSetMetaData(arrow, ResultTypes.of(delegate, arrow.getColumnCount()), nestedJson);
		}
		return meta;
	}

	@Override
	public ResultSetMetaData getMetaData() throws SQLException {
		return meta();
	}

	@Override
	public Object getObject(int column) throws SQLException {
		DuckTypes.Type type = meta().type(column);
		if (type == null || !type.isNested()) {
			return delegate.getObject(column);
		}
		if (type.isList() && !nestedJson) {
			return delegate.getArray(column); // Arrow's java.sql.Array
		}
		Object raw = delegate.getObject(column);
		if (raw == null) {
			return null;
		}
		Object value = NestedValues.normalize(raw, type);
		return nestedJson ? NestedValues.json(value) : value;
	}

	@Override
	public Object getObject(String label) throws SQLException {
		return getObject(findColumn(label));
	}

	@Override
	public String getString(int column) throws SQLException {
		DuckTypes.Type type = meta().type(column);
		if (type == null || !type.isNested()) {
			return delegate.getString(column);
		}
		Object raw = delegate.getObject(column);
		return raw == null ? null : NestedValues.json(NestedValues.normalize(raw, type));
	}

	@Override
	public String getString(String label) throws SQLException {
		return getString(findColumn(label));
	}

	@Override
	public Statement getStatement() {
		return statement;
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
