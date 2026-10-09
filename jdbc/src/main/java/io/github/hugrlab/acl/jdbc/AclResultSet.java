package io.github.hugrlab.acl.jdbc;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

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

	/** A BIGNUM / BIT as its text (duckdb's bytes on the wire), or null when the column is neither. */
	private String bits(DuckTypes.Type type, int column) throws SQLException {
		Object raw = delegate.getObject(column);
		if (raw == null) {
			return null;
		}
		return raw instanceof byte[] bytes ? DuckTypes.decodeBits(type, bytes) : raw.toString();
	}

	@Override
	public Object getObject(int column) throws SQLException {
		DuckTypes.Type type = meta().type(column);
		if (DuckTypes.isBits(type)) {
			return bits(type, column);
		}
		if (type == null || !type.isNested()) {
			return delegate.getObject(column);
		}
		Object raw = delegate.getObject(column);
		if (raw == null) {
			return null;
		}
		Object value = NestedValues.normalize(raw, type);
		if (nestedJson) {
			return NestedValues.json(value);
		}
		// a LIST as java.sql.Array whose elements are JDBC objects: a STRUCT element an AclStruct
		return type.isList() && value instanceof List<?> list ? new AclArray(type.element(), list) : value;
	}

	@Override
	public Object getObject(String label) throws SQLException {
		return getObject(findColumn(label));
	}

	@Override
	public <T> T getObject(int column, Class<T> wanted) throws SQLException {
		DuckTypes.Type type = meta().type(column);
		if (DuckTypes.isBits(type) && wanted == String.class) {
			return wanted.cast(bits(type, column));
		}
		if (type == null || !type.isNested()) {
			return delegate.getObject(column, wanted);
		}
		if (wanted == String.class) {
			return wanted.cast(getString(column));
		}
		Object value = getObject(column);
		if (value == null || wanted.isInstance(value)) {
			return wanted.cast(value);
		}
		throw new SQLException("a " + type.text() + " value is not a " + wanted.getName());
	}

	@Override
	public <T> T getObject(String label, Class<T> wanted) throws SQLException {
		return getObject(findColumn(label), wanted);
	}

	@Override
	public Array getArray(int column) throws SQLException {
		DuckTypes.Type type = meta().type(column);
		if (type == null || !type.isList() || nestedJson) {
			return delegate.getArray(column);
		}
		return (Array) getObject(column);
	}

	@Override
	public Array getArray(String label) throws SQLException {
		return getArray(findColumn(label));
	}

	@Override
	public String getString(int column) throws SQLException {
		DuckTypes.Type type = meta().type(column);
		if (DuckTypes.isBits(type)) {
			return bits(type, column);
		}
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
	public java.math.BigDecimal getBigDecimal(int column) throws SQLException {
		DuckTypes.Type type = meta().type(column);
		if (type != null && "BIGNUM".equals(type.base())) {
			String text = bits(type, column);
			return text == null ? null : new java.math.BigDecimal(text);
		}
		return delegate.getBigDecimal(column);
	}

	@Override
	public java.math.BigDecimal getBigDecimal(String label) throws SQLException {
		return getBigDecimal(findColumn(label));
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
