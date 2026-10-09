package io.github.hugrlab.acl.jdbc;

// GENERATED from the JDK's java.sql interfaces (spec 006): every method forwards to the delegate.
// Regenerate rather than edit; the behaviour lives in the subclasses.

@SuppressWarnings({"deprecation", "unused"})
abstract class ForwardingCallableStatement<S extends java.sql.CallableStatement> extends ForwardingPreparedStatement<S> implements java.sql.CallableStatement {
	protected ForwardingCallableStatement(S delegate) {
		super(delegate);
	}

	@Override
	public java.sql.Array getArray(String p0) throws java.sql.SQLException {
		return delegate.getArray(p0);
	}

	@Override
	public java.sql.Array getArray(int p0) throws java.sql.SQLException {
		return delegate.getArray(p0);
	}

	@Override
	public java.math.BigDecimal getBigDecimal(String p0) throws java.sql.SQLException {
		return delegate.getBigDecimal(p0);
	}

	@Override
	public java.math.BigDecimal getBigDecimal(int p0, int p1) throws java.sql.SQLException {
		return delegate.getBigDecimal(p0, p1);
	}

	@Override
	public java.math.BigDecimal getBigDecimal(int p0) throws java.sql.SQLException {
		return delegate.getBigDecimal(p0);
	}

	@Override
	public java.sql.Blob getBlob(String p0) throws java.sql.SQLException {
		return delegate.getBlob(p0);
	}

	@Override
	public java.sql.Blob getBlob(int p0) throws java.sql.SQLException {
		return delegate.getBlob(p0);
	}

	@Override
	public boolean getBoolean(String p0) throws java.sql.SQLException {
		return delegate.getBoolean(p0);
	}

	@Override
	public boolean getBoolean(int p0) throws java.sql.SQLException {
		return delegate.getBoolean(p0);
	}

	@Override
	public byte getByte(String p0) throws java.sql.SQLException {
		return delegate.getByte(p0);
	}

	@Override
	public byte getByte(int p0) throws java.sql.SQLException {
		return delegate.getByte(p0);
	}

	@Override
	public byte[] getBytes(String p0) throws java.sql.SQLException {
		return delegate.getBytes(p0);
	}

	@Override
	public byte[] getBytes(int p0) throws java.sql.SQLException {
		return delegate.getBytes(p0);
	}

	@Override
	public java.io.Reader getCharacterStream(String p0) throws java.sql.SQLException {
		return delegate.getCharacterStream(p0);
	}

	@Override
	public java.io.Reader getCharacterStream(int p0) throws java.sql.SQLException {
		return delegate.getCharacterStream(p0);
	}

	@Override
	public java.sql.Clob getClob(String p0) throws java.sql.SQLException {
		return delegate.getClob(p0);
	}

	@Override
	public java.sql.Clob getClob(int p0) throws java.sql.SQLException {
		return delegate.getClob(p0);
	}

	@Override
	public java.sql.Date getDate(String p0, java.util.Calendar p1) throws java.sql.SQLException {
		return delegate.getDate(p0, p1);
	}

	@Override
	public java.sql.Date getDate(String p0) throws java.sql.SQLException {
		return delegate.getDate(p0);
	}

	@Override
	public java.sql.Date getDate(int p0, java.util.Calendar p1) throws java.sql.SQLException {
		return delegate.getDate(p0, p1);
	}

	@Override
	public java.sql.Date getDate(int p0) throws java.sql.SQLException {
		return delegate.getDate(p0);
	}

	@Override
	public double getDouble(String p0) throws java.sql.SQLException {
		return delegate.getDouble(p0);
	}

	@Override
	public double getDouble(int p0) throws java.sql.SQLException {
		return delegate.getDouble(p0);
	}

	@Override
	public float getFloat(String p0) throws java.sql.SQLException {
		return delegate.getFloat(p0);
	}

	@Override
	public float getFloat(int p0) throws java.sql.SQLException {
		return delegate.getFloat(p0);
	}

	@Override
	public int getInt(String p0) throws java.sql.SQLException {
		return delegate.getInt(p0);
	}

	@Override
	public int getInt(int p0) throws java.sql.SQLException {
		return delegate.getInt(p0);
	}

	@Override
	public long getLong(String p0) throws java.sql.SQLException {
		return delegate.getLong(p0);
	}

	@Override
	public long getLong(int p0) throws java.sql.SQLException {
		return delegate.getLong(p0);
	}

	@Override
	public java.io.Reader getNCharacterStream(String p0) throws java.sql.SQLException {
		return delegate.getNCharacterStream(p0);
	}

	@Override
	public java.io.Reader getNCharacterStream(int p0) throws java.sql.SQLException {
		return delegate.getNCharacterStream(p0);
	}

	@Override
	public java.sql.NClob getNClob(String p0) throws java.sql.SQLException {
		return delegate.getNClob(p0);
	}

	@Override
	public java.sql.NClob getNClob(int p0) throws java.sql.SQLException {
		return delegate.getNClob(p0);
	}

	@Override
	public String getNString(String p0) throws java.sql.SQLException {
		return delegate.getNString(p0);
	}

	@Override
	public String getNString(int p0) throws java.sql.SQLException {
		return delegate.getNString(p0);
	}

	@Override
	public <T> T getObject(String p0, Class<T> p1) throws java.sql.SQLException {
		return delegate.getObject(p0, p1);
	}

	@Override
	public Object getObject(String p0, java.util.Map<String, Class<?>> p1) throws java.sql.SQLException {
		return delegate.getObject(p0, p1);
	}

	@Override
	public Object getObject(String p0) throws java.sql.SQLException {
		return delegate.getObject(p0);
	}

	@Override
	public <T> T getObject(int p0, Class<T> p1) throws java.sql.SQLException {
		return delegate.getObject(p0, p1);
	}

	@Override
	public Object getObject(int p0, java.util.Map<String, Class<?>> p1) throws java.sql.SQLException {
		return delegate.getObject(p0, p1);
	}

	@Override
	public Object getObject(int p0) throws java.sql.SQLException {
		return delegate.getObject(p0);
	}

	@Override
	public java.sql.Ref getRef(String p0) throws java.sql.SQLException {
		return delegate.getRef(p0);
	}

	@Override
	public java.sql.Ref getRef(int p0) throws java.sql.SQLException {
		return delegate.getRef(p0);
	}

	@Override
	public java.sql.RowId getRowId(String p0) throws java.sql.SQLException {
		return delegate.getRowId(p0);
	}

	@Override
	public java.sql.RowId getRowId(int p0) throws java.sql.SQLException {
		return delegate.getRowId(p0);
	}

	@Override
	public java.sql.SQLXML getSQLXML(String p0) throws java.sql.SQLException {
		return delegate.getSQLXML(p0);
	}

	@Override
	public java.sql.SQLXML getSQLXML(int p0) throws java.sql.SQLException {
		return delegate.getSQLXML(p0);
	}

	@Override
	public short getShort(String p0) throws java.sql.SQLException {
		return delegate.getShort(p0);
	}

	@Override
	public short getShort(int p0) throws java.sql.SQLException {
		return delegate.getShort(p0);
	}

	@Override
	public String getString(String p0) throws java.sql.SQLException {
		return delegate.getString(p0);
	}

	@Override
	public String getString(int p0) throws java.sql.SQLException {
		return delegate.getString(p0);
	}

	@Override
	public java.sql.Time getTime(String p0, java.util.Calendar p1) throws java.sql.SQLException {
		return delegate.getTime(p0, p1);
	}

	@Override
	public java.sql.Time getTime(String p0) throws java.sql.SQLException {
		return delegate.getTime(p0);
	}

	@Override
	public java.sql.Time getTime(int p0, java.util.Calendar p1) throws java.sql.SQLException {
		return delegate.getTime(p0, p1);
	}

	@Override
	public java.sql.Time getTime(int p0) throws java.sql.SQLException {
		return delegate.getTime(p0);
	}

	@Override
	public java.sql.Timestamp getTimestamp(String p0, java.util.Calendar p1) throws java.sql.SQLException {
		return delegate.getTimestamp(p0, p1);
	}

	@Override
	public java.sql.Timestamp getTimestamp(String p0) throws java.sql.SQLException {
		return delegate.getTimestamp(p0);
	}

	@Override
	public java.sql.Timestamp getTimestamp(int p0, java.util.Calendar p1) throws java.sql.SQLException {
		return delegate.getTimestamp(p0, p1);
	}

	@Override
	public java.sql.Timestamp getTimestamp(int p0) throws java.sql.SQLException {
		return delegate.getTimestamp(p0);
	}

	@Override
	public java.net.URL getURL(String p0) throws java.sql.SQLException {
		return delegate.getURL(p0);
	}

	@Override
	public java.net.URL getURL(int p0) throws java.sql.SQLException {
		return delegate.getURL(p0);
	}

	@Override
	public void registerOutParameter(String p0, int p1, String p2) throws java.sql.SQLException {
		delegate.registerOutParameter(p0, p1, p2);
	}

	@Override
	public void registerOutParameter(String p0, int p1, int p2) throws java.sql.SQLException {
		delegate.registerOutParameter(p0, p1, p2);
	}

	@Override
	public void registerOutParameter(String p0, int p1) throws java.sql.SQLException {
		delegate.registerOutParameter(p0, p1);
	}

	@Override
	public void registerOutParameter(String p0, java.sql.SQLType p1, String p2) throws java.sql.SQLException {
		delegate.registerOutParameter(p0, p1, p2);
	}

	@Override
	public void registerOutParameter(String p0, java.sql.SQLType p1, int p2) throws java.sql.SQLException {
		delegate.registerOutParameter(p0, p1, p2);
	}

	@Override
	public void registerOutParameter(String p0, java.sql.SQLType p1) throws java.sql.SQLException {
		delegate.registerOutParameter(p0, p1);
	}

	@Override
	public void registerOutParameter(int p0, int p1, String p2) throws java.sql.SQLException {
		delegate.registerOutParameter(p0, p1, p2);
	}

	@Override
	public void registerOutParameter(int p0, int p1, int p2) throws java.sql.SQLException {
		delegate.registerOutParameter(p0, p1, p2);
	}

	@Override
	public void registerOutParameter(int p0, int p1) throws java.sql.SQLException {
		delegate.registerOutParameter(p0, p1);
	}

	@Override
	public void registerOutParameter(int p0, java.sql.SQLType p1, String p2) throws java.sql.SQLException {
		delegate.registerOutParameter(p0, p1, p2);
	}

	@Override
	public void registerOutParameter(int p0, java.sql.SQLType p1, int p2) throws java.sql.SQLException {
		delegate.registerOutParameter(p0, p1, p2);
	}

	@Override
	public void registerOutParameter(int p0, java.sql.SQLType p1) throws java.sql.SQLException {
		delegate.registerOutParameter(p0, p1);
	}

	@Override
	public void setAsciiStream(String p0, java.io.InputStream p1, int p2) throws java.sql.SQLException {
		delegate.setAsciiStream(p0, p1, p2);
	}

	@Override
	public void setAsciiStream(String p0, java.io.InputStream p1, long p2) throws java.sql.SQLException {
		delegate.setAsciiStream(p0, p1, p2);
	}

	@Override
	public void setAsciiStream(String p0, java.io.InputStream p1) throws java.sql.SQLException {
		delegate.setAsciiStream(p0, p1);
	}

	@Override
	public void setBigDecimal(String p0, java.math.BigDecimal p1) throws java.sql.SQLException {
		delegate.setBigDecimal(p0, p1);
	}

	@Override
	public void setBinaryStream(String p0, java.io.InputStream p1, int p2) throws java.sql.SQLException {
		delegate.setBinaryStream(p0, p1, p2);
	}

	@Override
	public void setBinaryStream(String p0, java.io.InputStream p1, long p2) throws java.sql.SQLException {
		delegate.setBinaryStream(p0, p1, p2);
	}

	@Override
	public void setBinaryStream(String p0, java.io.InputStream p1) throws java.sql.SQLException {
		delegate.setBinaryStream(p0, p1);
	}

	@Override
	public void setBlob(String p0, java.io.InputStream p1, long p2) throws java.sql.SQLException {
		delegate.setBlob(p0, p1, p2);
	}

	@Override
	public void setBlob(String p0, java.io.InputStream p1) throws java.sql.SQLException {
		delegate.setBlob(p0, p1);
	}

	@Override
	public void setBlob(String p0, java.sql.Blob p1) throws java.sql.SQLException {
		delegate.setBlob(p0, p1);
	}

	@Override
	public void setBoolean(String p0, boolean p1) throws java.sql.SQLException {
		delegate.setBoolean(p0, p1);
	}

	@Override
	public void setByte(String p0, byte p1) throws java.sql.SQLException {
		delegate.setByte(p0, p1);
	}

	@Override
	public void setBytes(String p0, byte[] p1) throws java.sql.SQLException {
		delegate.setBytes(p0, p1);
	}

	@Override
	public void setCharacterStream(String p0, java.io.Reader p1, int p2) throws java.sql.SQLException {
		delegate.setCharacterStream(p0, p1, p2);
	}

	@Override
	public void setCharacterStream(String p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		delegate.setCharacterStream(p0, p1, p2);
	}

	@Override
	public void setCharacterStream(String p0, java.io.Reader p1) throws java.sql.SQLException {
		delegate.setCharacterStream(p0, p1);
	}

	@Override
	public void setClob(String p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		delegate.setClob(p0, p1, p2);
	}

	@Override
	public void setClob(String p0, java.io.Reader p1) throws java.sql.SQLException {
		delegate.setClob(p0, p1);
	}

	@Override
	public void setClob(String p0, java.sql.Clob p1) throws java.sql.SQLException {
		delegate.setClob(p0, p1);
	}

	@Override
	public void setDate(String p0, java.sql.Date p1, java.util.Calendar p2) throws java.sql.SQLException {
		delegate.setDate(p0, p1, p2);
	}

	@Override
	public void setDate(String p0, java.sql.Date p1) throws java.sql.SQLException {
		delegate.setDate(p0, p1);
	}

	@Override
	public void setDouble(String p0, double p1) throws java.sql.SQLException {
		delegate.setDouble(p0, p1);
	}

	@Override
	public void setFloat(String p0, float p1) throws java.sql.SQLException {
		delegate.setFloat(p0, p1);
	}

	@Override
	public void setInt(String p0, int p1) throws java.sql.SQLException {
		delegate.setInt(p0, p1);
	}

	@Override
	public void setLong(String p0, long p1) throws java.sql.SQLException {
		delegate.setLong(p0, p1);
	}

	@Override
	public void setNCharacterStream(String p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		delegate.setNCharacterStream(p0, p1, p2);
	}

	@Override
	public void setNCharacterStream(String p0, java.io.Reader p1) throws java.sql.SQLException {
		delegate.setNCharacterStream(p0, p1);
	}

	@Override
	public void setNClob(String p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		delegate.setNClob(p0, p1, p2);
	}

	@Override
	public void setNClob(String p0, java.io.Reader p1) throws java.sql.SQLException {
		delegate.setNClob(p0, p1);
	}

	@Override
	public void setNClob(String p0, java.sql.NClob p1) throws java.sql.SQLException {
		delegate.setNClob(p0, p1);
	}

	@Override
	public void setNString(String p0, String p1) throws java.sql.SQLException {
		delegate.setNString(p0, p1);
	}

	@Override
	public void setNull(String p0, int p1, String p2) throws java.sql.SQLException {
		delegate.setNull(p0, p1, p2);
	}

	@Override
	public void setNull(String p0, int p1) throws java.sql.SQLException {
		delegate.setNull(p0, p1);
	}

	@Override
	public void setObject(String p0, Object p1, int p2, int p3) throws java.sql.SQLException {
		delegate.setObject(p0, p1, p2, p3);
	}

	@Override
	public void setObject(String p0, Object p1, int p2) throws java.sql.SQLException {
		delegate.setObject(p0, p1, p2);
	}

	@Override
	public void setObject(String p0, Object p1, java.sql.SQLType p2, int p3) throws java.sql.SQLException {
		delegate.setObject(p0, p1, p2, p3);
	}

	@Override
	public void setObject(String p0, Object p1, java.sql.SQLType p2) throws java.sql.SQLException {
		delegate.setObject(p0, p1, p2);
	}

	@Override
	public void setObject(String p0, Object p1) throws java.sql.SQLException {
		delegate.setObject(p0, p1);
	}

	@Override
	public void setRowId(String p0, java.sql.RowId p1) throws java.sql.SQLException {
		delegate.setRowId(p0, p1);
	}

	@Override
	public void setSQLXML(String p0, java.sql.SQLXML p1) throws java.sql.SQLException {
		delegate.setSQLXML(p0, p1);
	}

	@Override
	public void setShort(String p0, short p1) throws java.sql.SQLException {
		delegate.setShort(p0, p1);
	}

	@Override
	public void setString(String p0, String p1) throws java.sql.SQLException {
		delegate.setString(p0, p1);
	}

	@Override
	public void setTime(String p0, java.sql.Time p1, java.util.Calendar p2) throws java.sql.SQLException {
		delegate.setTime(p0, p1, p2);
	}

	@Override
	public void setTime(String p0, java.sql.Time p1) throws java.sql.SQLException {
		delegate.setTime(p0, p1);
	}

	@Override
	public void setTimestamp(String p0, java.sql.Timestamp p1, java.util.Calendar p2) throws java.sql.SQLException {
		delegate.setTimestamp(p0, p1, p2);
	}

	@Override
	public void setTimestamp(String p0, java.sql.Timestamp p1) throws java.sql.SQLException {
		delegate.setTimestamp(p0, p1);
	}

	@Override
	public void setURL(String p0, java.net.URL p1) throws java.sql.SQLException {
		delegate.setURL(p0, p1);
	}

	@Override
	public boolean wasNull() throws java.sql.SQLException {
		return delegate.wasNull();
	}
}
