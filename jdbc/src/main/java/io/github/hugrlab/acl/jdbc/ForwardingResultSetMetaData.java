package io.github.hugrlab.acl.jdbc;

// GENERATED from the JDK's java.sql interfaces (spec 006): every method forwards to the delegate.
// Regenerate rather than edit; the behaviour lives in the subclasses.

@SuppressWarnings({"deprecation", "unused"})
abstract class ForwardingResultSetMetaData implements java.sql.ResultSetMetaData {
	protected final java.sql.ResultSetMetaData delegate;

	protected ForwardingResultSetMetaData(java.sql.ResultSetMetaData delegate) {
		this.delegate = delegate;
	}

	@Override
	public String getCatalogName(int p0) throws java.sql.SQLException {
		return delegate.getCatalogName(p0);
	}

	@Override
	public String getColumnClassName(int p0) throws java.sql.SQLException {
		return delegate.getColumnClassName(p0);
	}

	@Override
	public int getColumnCount() throws java.sql.SQLException {
		return delegate.getColumnCount();
	}

	@Override
	public int getColumnDisplaySize(int p0) throws java.sql.SQLException {
		return delegate.getColumnDisplaySize(p0);
	}

	@Override
	public String getColumnLabel(int p0) throws java.sql.SQLException {
		return delegate.getColumnLabel(p0);
	}

	@Override
	public String getColumnName(int p0) throws java.sql.SQLException {
		return delegate.getColumnName(p0);
	}

	@Override
	public int getColumnType(int p0) throws java.sql.SQLException {
		return delegate.getColumnType(p0);
	}

	@Override
	public String getColumnTypeName(int p0) throws java.sql.SQLException {
		return delegate.getColumnTypeName(p0);
	}

	@Override
	public int getPrecision(int p0) throws java.sql.SQLException {
		return delegate.getPrecision(p0);
	}

	@Override
	public int getScale(int p0) throws java.sql.SQLException {
		return delegate.getScale(p0);
	}

	@Override
	public String getSchemaName(int p0) throws java.sql.SQLException {
		return delegate.getSchemaName(p0);
	}

	@Override
	public String getTableName(int p0) throws java.sql.SQLException {
		return delegate.getTableName(p0);
	}

	@Override
	public boolean isAutoIncrement(int p0) throws java.sql.SQLException {
		return delegate.isAutoIncrement(p0);
	}

	@Override
	public boolean isCaseSensitive(int p0) throws java.sql.SQLException {
		return delegate.isCaseSensitive(p0);
	}

	@Override
	public boolean isCurrency(int p0) throws java.sql.SQLException {
		return delegate.isCurrency(p0);
	}

	@Override
	public boolean isDefinitelyWritable(int p0) throws java.sql.SQLException {
		return delegate.isDefinitelyWritable(p0);
	}

	@Override
	public int isNullable(int p0) throws java.sql.SQLException {
		return delegate.isNullable(p0);
	}

	@Override
	public boolean isReadOnly(int p0) throws java.sql.SQLException {
		return delegate.isReadOnly(p0);
	}

	@Override
	public boolean isSearchable(int p0) throws java.sql.SQLException {
		return delegate.isSearchable(p0);
	}

	@Override
	public boolean isSigned(int p0) throws java.sql.SQLException {
		return delegate.isSigned(p0);
	}

	@Override
	public boolean isWrapperFor(Class<?> p0) throws java.sql.SQLException {
		return delegate.isWrapperFor(p0);
	}

	@Override
	public boolean isWritable(int p0) throws java.sql.SQLException {
		return delegate.isWritable(p0);
	}

	@Override
	public <T> T unwrap(Class<T> p0) throws java.sql.SQLException {
		return delegate.unwrap(p0);
	}
}
