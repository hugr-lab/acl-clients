package io.github.hugrlab.acl.jdbc;

import java.sql.Array;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Struct;
import java.util.Map;

/**
 * A statement result's columns as duckdb names them (spec 006): {@code getColumnTypeName} is the
 * field's {@code ARROW:FLIGHT:SQL:TYPE_NAME} ({@code STRUCT(city VARCHAR, ...)}, not Arrow's generic
 * {@code JAVA_OBJECT}), {@code getColumnType} its JDBC code by {@link DuckTypes}, and the class of a
 * nested column what {@link AclResultSet#getObject(int)} returns. A column without the metadata keeps
 * Arrow's answers.
 */
final class AclResultSetMetaData extends ForwardingResultSetMetaData {
	private final DuckTypes.Type[] types;
	private final boolean nestedJson;

	AclResultSetMetaData(ResultSetMetaData arrow, String[] typeNames, boolean nestedJson) {
		super(arrow);
		this.nestedJson = nestedJson;
		this.types = new DuckTypes.Type[typeNames == null ? 0 : typeNames.length];
		for (int i = 0; i < types.length; i++) {
			types[i] = typeNames[i] == null ? null : DuckTypes.parse(typeNames[i]);
		}
	}

	/** The column's duckdb type, or null when the node did not name it. */
	DuckTypes.Type type(int column) {
		return column >= 1 && column <= types.length ? types[column - 1] : null;
	}

	@Override
	public String getColumnTypeName(int column) throws SQLException {
		DuckTypes.Type t = type(column);
		return t == null ? delegate.getColumnTypeName(column) : t.text();
	}

	@Override
	public int getColumnType(int column) throws SQLException {
		DuckTypes.Type t = type(column);
		if (t == null) {
			return delegate.getColumnType(column);
		}
		if (nestedJson && t.isNested()) {
			return java.sql.Types.VARCHAR;
		}
		return t.jdbcType();
	}

	@Override
	public String getColumnClassName(int column) throws SQLException {
		DuckTypes.Type t = type(column);
		if (DuckTypes.isBits(t)) {
			return DuckTypes.javaClass(t); // AclResultSet decodes them; Arrow's would be its bytes
		}
		if (t == null || !t.isNested()) {
			// what Arrow's getObject returns for a scalar - Arrow's metadata leaves it null
			String arrow = delegate.getColumnClassName(column);
			return arrow != null || t == null ? arrow : DuckTypes.javaClass(t);
		}
		if (nestedJson) {
			return String.class.getName();
		}
		if (t.isStruct()) {
			return Struct.class.getName();
		}
		if (t.isList()) {
			return Array.class.getName();
		}
		return t.isMap() ? Map.class.getName() : delegate.getColumnClassName(column);
	}

	@Override
	public int getPrecision(int column) throws SQLException {
		DuckTypes.Type t = type(column);
		return t != null && t.size() != null ? t.size() : delegate.getPrecision(column);
	}

	@Override
	public int getScale(int column) throws SQLException {
		DuckTypes.Type t = type(column);
		return t != null && t.digits() != null && t.isNumeric() ? t.digits() : delegate.getScale(column);
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
