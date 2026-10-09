package io.github.hugrlab.acl.jdbc;

// GENERATED from the JDK's java.sql interfaces (spec 006): every method forwards to the delegate.
// Regenerate rather than edit; the behaviour lives in the subclasses.

@SuppressWarnings({"deprecation", "unused"})
abstract class ForwardingPreparedStatement<S extends java.sql.PreparedStatement> extends ForwardingStatement<S> implements java.sql.PreparedStatement {
	protected ForwardingPreparedStatement(S delegate) {
		super(delegate);
	}

	@Override
	public void addBatch() throws java.sql.SQLException {
		delegate.addBatch();
	}

	@Override
	public void clearParameters() throws java.sql.SQLException {
		delegate.clearParameters();
	}

	@Override
	public boolean execute() throws java.sql.SQLException {
		return delegate.execute();
	}

	@Override
	public long executeLargeUpdate() throws java.sql.SQLException {
		return delegate.executeLargeUpdate();
	}

	@Override
	public java.sql.ResultSet executeQuery() throws java.sql.SQLException {
		return delegate.executeQuery();
	}

	@Override
	public int executeUpdate() throws java.sql.SQLException {
		return delegate.executeUpdate();
	}

	@Override
	public java.sql.ResultSetMetaData getMetaData() throws java.sql.SQLException {
		return delegate.getMetaData();
	}

	@Override
	public java.sql.ParameterMetaData getParameterMetaData() throws java.sql.SQLException {
		return delegate.getParameterMetaData();
	}

	@Override
	public void setArray(int p0, java.sql.Array p1) throws java.sql.SQLException {
		delegate.setArray(p0, p1);
	}

	@Override
	public void setAsciiStream(int p0, java.io.InputStream p1, int p2) throws java.sql.SQLException {
		delegate.setAsciiStream(p0, p1, p2);
	}

	@Override
	public void setAsciiStream(int p0, java.io.InputStream p1, long p2) throws java.sql.SQLException {
		delegate.setAsciiStream(p0, p1, p2);
	}

	@Override
	public void setAsciiStream(int p0, java.io.InputStream p1) throws java.sql.SQLException {
		delegate.setAsciiStream(p0, p1);
	}

	@Override
	public void setBigDecimal(int p0, java.math.BigDecimal p1) throws java.sql.SQLException {
		delegate.setBigDecimal(p0, p1);
	}

	@Override
	public void setBinaryStream(int p0, java.io.InputStream p1, int p2) throws java.sql.SQLException {
		delegate.setBinaryStream(p0, p1, p2);
	}

	@Override
	public void setBinaryStream(int p0, java.io.InputStream p1, long p2) throws java.sql.SQLException {
		delegate.setBinaryStream(p0, p1, p2);
	}

	@Override
	public void setBinaryStream(int p0, java.io.InputStream p1) throws java.sql.SQLException {
		delegate.setBinaryStream(p0, p1);
	}

	@Override
	public void setBlob(int p0, java.io.InputStream p1, long p2) throws java.sql.SQLException {
		delegate.setBlob(p0, p1, p2);
	}

	@Override
	public void setBlob(int p0, java.io.InputStream p1) throws java.sql.SQLException {
		delegate.setBlob(p0, p1);
	}

	@Override
	public void setBlob(int p0, java.sql.Blob p1) throws java.sql.SQLException {
		delegate.setBlob(p0, p1);
	}

	@Override
	public void setBoolean(int p0, boolean p1) throws java.sql.SQLException {
		delegate.setBoolean(p0, p1);
	}

	@Override
	public void setByte(int p0, byte p1) throws java.sql.SQLException {
		delegate.setByte(p0, p1);
	}

	@Override
	public void setBytes(int p0, byte[] p1) throws java.sql.SQLException {
		delegate.setBytes(p0, p1);
	}

	@Override
	public void setCharacterStream(int p0, java.io.Reader p1, int p2) throws java.sql.SQLException {
		delegate.setCharacterStream(p0, p1, p2);
	}

	@Override
	public void setCharacterStream(int p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		delegate.setCharacterStream(p0, p1, p2);
	}

	@Override
	public void setCharacterStream(int p0, java.io.Reader p1) throws java.sql.SQLException {
		delegate.setCharacterStream(p0, p1);
	}

	@Override
	public void setClob(int p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		delegate.setClob(p0, p1, p2);
	}

	@Override
	public void setClob(int p0, java.io.Reader p1) throws java.sql.SQLException {
		delegate.setClob(p0, p1);
	}

	@Override
	public void setClob(int p0, java.sql.Clob p1) throws java.sql.SQLException {
		delegate.setClob(p0, p1);
	}

	@Override
	public void setDate(int p0, java.sql.Date p1, java.util.Calendar p2) throws java.sql.SQLException {
		delegate.setDate(p0, p1, p2);
	}

	@Override
	public void setDate(int p0, java.sql.Date p1) throws java.sql.SQLException {
		delegate.setDate(p0, p1);
	}

	@Override
	public void setDouble(int p0, double p1) throws java.sql.SQLException {
		delegate.setDouble(p0, p1);
	}

	@Override
	public void setFloat(int p0, float p1) throws java.sql.SQLException {
		delegate.setFloat(p0, p1);
	}

	@Override
	public void setInt(int p0, int p1) throws java.sql.SQLException {
		delegate.setInt(p0, p1);
	}

	@Override
	public void setLong(int p0, long p1) throws java.sql.SQLException {
		delegate.setLong(p0, p1);
	}

	@Override
	public void setNCharacterStream(int p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		delegate.setNCharacterStream(p0, p1, p2);
	}

	@Override
	public void setNCharacterStream(int p0, java.io.Reader p1) throws java.sql.SQLException {
		delegate.setNCharacterStream(p0, p1);
	}

	@Override
	public void setNClob(int p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		delegate.setNClob(p0, p1, p2);
	}

	@Override
	public void setNClob(int p0, java.io.Reader p1) throws java.sql.SQLException {
		delegate.setNClob(p0, p1);
	}

	@Override
	public void setNClob(int p0, java.sql.NClob p1) throws java.sql.SQLException {
		delegate.setNClob(p0, p1);
	}

	@Override
	public void setNString(int p0, String p1) throws java.sql.SQLException {
		delegate.setNString(p0, p1);
	}

	@Override
	public void setNull(int p0, int p1, String p2) throws java.sql.SQLException {
		delegate.setNull(p0, p1, p2);
	}

	@Override
	public void setNull(int p0, int p1) throws java.sql.SQLException {
		delegate.setNull(p0, p1);
	}

	@Override
	public void setObject(int p0, Object p1, int p2, int p3) throws java.sql.SQLException {
		delegate.setObject(p0, p1, p2, p3);
	}

	@Override
	public void setObject(int p0, Object p1, int p2) throws java.sql.SQLException {
		delegate.setObject(p0, p1, p2);
	}

	@Override
	public void setObject(int p0, Object p1, java.sql.SQLType p2, int p3) throws java.sql.SQLException {
		delegate.setObject(p0, p1, p2, p3);
	}

	@Override
	public void setObject(int p0, Object p1, java.sql.SQLType p2) throws java.sql.SQLException {
		delegate.setObject(p0, p1, p2);
	}

	@Override
	public void setObject(int p0, Object p1) throws java.sql.SQLException {
		delegate.setObject(p0, p1);
	}

	@Override
	public void setRef(int p0, java.sql.Ref p1) throws java.sql.SQLException {
		delegate.setRef(p0, p1);
	}

	@Override
	public void setRowId(int p0, java.sql.RowId p1) throws java.sql.SQLException {
		delegate.setRowId(p0, p1);
	}

	@Override
	public void setSQLXML(int p0, java.sql.SQLXML p1) throws java.sql.SQLException {
		delegate.setSQLXML(p0, p1);
	}

	@Override
	public void setShort(int p0, short p1) throws java.sql.SQLException {
		delegate.setShort(p0, p1);
	}

	@Override
	public void setString(int p0, String p1) throws java.sql.SQLException {
		delegate.setString(p0, p1);
	}

	@Override
	public void setTime(int p0, java.sql.Time p1, java.util.Calendar p2) throws java.sql.SQLException {
		delegate.setTime(p0, p1, p2);
	}

	@Override
	public void setTime(int p0, java.sql.Time p1) throws java.sql.SQLException {
		delegate.setTime(p0, p1);
	}

	@Override
	public void setTimestamp(int p0, java.sql.Timestamp p1, java.util.Calendar p2) throws java.sql.SQLException {
		delegate.setTimestamp(p0, p1, p2);
	}

	@Override
	public void setTimestamp(int p0, java.sql.Timestamp p1) throws java.sql.SQLException {
		delegate.setTimestamp(p0, p1);
	}

	@Override
	public void setURL(int p0, java.net.URL p1) throws java.sql.SQLException {
		delegate.setURL(p0, p1);
	}

	@Override
	public void setUnicodeStream(int p0, java.io.InputStream p1, int p2) throws java.sql.SQLException {
		delegate.setUnicodeStream(p0, p1, p2);
	}
}
