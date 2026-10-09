package io.github.hugrlab.acl.jdbc;

// GENERATED from the JDK's java.sql interfaces (spec 006): every method forwards to the delegate.
// Regenerate rather than edit; the behaviour lives in the subclasses.

@SuppressWarnings({"deprecation", "unused"})
abstract class ForwardingResultSet implements java.sql.ResultSet {
	protected final java.sql.ResultSet delegate;

	protected ForwardingResultSet(java.sql.ResultSet delegate) {
		this.delegate = delegate;
	}

	@Override
	public boolean absolute(int p0) throws java.sql.SQLException {
		return delegate.absolute(p0);
	}

	@Override
	public void afterLast() throws java.sql.SQLException {
		delegate.afterLast();
	}

	@Override
	public void beforeFirst() throws java.sql.SQLException {
		delegate.beforeFirst();
	}

	@Override
	public void cancelRowUpdates() throws java.sql.SQLException {
		delegate.cancelRowUpdates();
	}

	@Override
	public void clearWarnings() throws java.sql.SQLException {
		delegate.clearWarnings();
	}

	@Override
	public void close() throws java.sql.SQLException {
		delegate.close();
	}

	@Override
	public void deleteRow() throws java.sql.SQLException {
		delegate.deleteRow();
	}

	@Override
	public int findColumn(String p0) throws java.sql.SQLException {
		return delegate.findColumn(p0);
	}

	@Override
	public boolean first() throws java.sql.SQLException {
		return delegate.first();
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
	public java.io.InputStream getAsciiStream(String p0) throws java.sql.SQLException {
		return delegate.getAsciiStream(p0);
	}

	@Override
	public java.io.InputStream getAsciiStream(int p0) throws java.sql.SQLException {
		return delegate.getAsciiStream(p0);
	}

	@Override
	public java.math.BigDecimal getBigDecimal(String p0, int p1) throws java.sql.SQLException {
		return delegate.getBigDecimal(p0, p1);
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
	public java.io.InputStream getBinaryStream(String p0) throws java.sql.SQLException {
		return delegate.getBinaryStream(p0);
	}

	@Override
	public java.io.InputStream getBinaryStream(int p0) throws java.sql.SQLException {
		return delegate.getBinaryStream(p0);
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
	public int getConcurrency() throws java.sql.SQLException {
		return delegate.getConcurrency();
	}

	@Override
	public String getCursorName() throws java.sql.SQLException {
		return delegate.getCursorName();
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
	public int getFetchDirection() throws java.sql.SQLException {
		return delegate.getFetchDirection();
	}

	@Override
	public int getFetchSize() throws java.sql.SQLException {
		return delegate.getFetchSize();
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
	public int getHoldability() throws java.sql.SQLException {
		return delegate.getHoldability();
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
	public java.sql.ResultSetMetaData getMetaData() throws java.sql.SQLException {
		return delegate.getMetaData();
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
	public int getRow() throws java.sql.SQLException {
		return delegate.getRow();
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
	public java.sql.Statement getStatement() throws java.sql.SQLException {
		return delegate.getStatement();
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
	public int getType() throws java.sql.SQLException {
		return delegate.getType();
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
	public java.io.InputStream getUnicodeStream(String p0) throws java.sql.SQLException {
		return delegate.getUnicodeStream(p0);
	}

	@Override
	public java.io.InputStream getUnicodeStream(int p0) throws java.sql.SQLException {
		return delegate.getUnicodeStream(p0);
	}

	@Override
	public java.sql.SQLWarning getWarnings() throws java.sql.SQLException {
		return delegate.getWarnings();
	}

	@Override
	public void insertRow() throws java.sql.SQLException {
		delegate.insertRow();
	}

	@Override
	public boolean isAfterLast() throws java.sql.SQLException {
		return delegate.isAfterLast();
	}

	@Override
	public boolean isBeforeFirst() throws java.sql.SQLException {
		return delegate.isBeforeFirst();
	}

	@Override
	public boolean isClosed() throws java.sql.SQLException {
		return delegate.isClosed();
	}

	@Override
	public boolean isFirst() throws java.sql.SQLException {
		return delegate.isFirst();
	}

	@Override
	public boolean isLast() throws java.sql.SQLException {
		return delegate.isLast();
	}

	@Override
	public boolean isWrapperFor(Class<?> p0) throws java.sql.SQLException {
		return delegate.isWrapperFor(p0);
	}

	@Override
	public boolean last() throws java.sql.SQLException {
		return delegate.last();
	}

	@Override
	public void moveToCurrentRow() throws java.sql.SQLException {
		delegate.moveToCurrentRow();
	}

	@Override
	public void moveToInsertRow() throws java.sql.SQLException {
		delegate.moveToInsertRow();
	}

	@Override
	public boolean next() throws java.sql.SQLException {
		return delegate.next();
	}

	@Override
	public boolean previous() throws java.sql.SQLException {
		return delegate.previous();
	}

	@Override
	public void refreshRow() throws java.sql.SQLException {
		delegate.refreshRow();
	}

	@Override
	public boolean relative(int p0) throws java.sql.SQLException {
		return delegate.relative(p0);
	}

	@Override
	public boolean rowDeleted() throws java.sql.SQLException {
		return delegate.rowDeleted();
	}

	@Override
	public boolean rowInserted() throws java.sql.SQLException {
		return delegate.rowInserted();
	}

	@Override
	public boolean rowUpdated() throws java.sql.SQLException {
		return delegate.rowUpdated();
	}

	@Override
	public void setFetchDirection(int p0) throws java.sql.SQLException {
		delegate.setFetchDirection(p0);
	}

	@Override
	public void setFetchSize(int p0) throws java.sql.SQLException {
		delegate.setFetchSize(p0);
	}

	@Override
	public <T> T unwrap(Class<T> p0) throws java.sql.SQLException {
		return delegate.unwrap(p0);
	}

	@Override
	public void updateArray(String p0, java.sql.Array p1) throws java.sql.SQLException {
		delegate.updateArray(p0, p1);
	}

	@Override
	public void updateArray(int p0, java.sql.Array p1) throws java.sql.SQLException {
		delegate.updateArray(p0, p1);
	}

	@Override
	public void updateAsciiStream(String p0, java.io.InputStream p1, int p2) throws java.sql.SQLException {
		delegate.updateAsciiStream(p0, p1, p2);
	}

	@Override
	public void updateAsciiStream(String p0, java.io.InputStream p1, long p2) throws java.sql.SQLException {
		delegate.updateAsciiStream(p0, p1, p2);
	}

	@Override
	public void updateAsciiStream(String p0, java.io.InputStream p1) throws java.sql.SQLException {
		delegate.updateAsciiStream(p0, p1);
	}

	@Override
	public void updateAsciiStream(int p0, java.io.InputStream p1, int p2) throws java.sql.SQLException {
		delegate.updateAsciiStream(p0, p1, p2);
	}

	@Override
	public void updateAsciiStream(int p0, java.io.InputStream p1, long p2) throws java.sql.SQLException {
		delegate.updateAsciiStream(p0, p1, p2);
	}

	@Override
	public void updateAsciiStream(int p0, java.io.InputStream p1) throws java.sql.SQLException {
		delegate.updateAsciiStream(p0, p1);
	}

	@Override
	public void updateBigDecimal(String p0, java.math.BigDecimal p1) throws java.sql.SQLException {
		delegate.updateBigDecimal(p0, p1);
	}

	@Override
	public void updateBigDecimal(int p0, java.math.BigDecimal p1) throws java.sql.SQLException {
		delegate.updateBigDecimal(p0, p1);
	}

	@Override
	public void updateBinaryStream(String p0, java.io.InputStream p1, int p2) throws java.sql.SQLException {
		delegate.updateBinaryStream(p0, p1, p2);
	}

	@Override
	public void updateBinaryStream(String p0, java.io.InputStream p1, long p2) throws java.sql.SQLException {
		delegate.updateBinaryStream(p0, p1, p2);
	}

	@Override
	public void updateBinaryStream(String p0, java.io.InputStream p1) throws java.sql.SQLException {
		delegate.updateBinaryStream(p0, p1);
	}

	@Override
	public void updateBinaryStream(int p0, java.io.InputStream p1, int p2) throws java.sql.SQLException {
		delegate.updateBinaryStream(p0, p1, p2);
	}

	@Override
	public void updateBinaryStream(int p0, java.io.InputStream p1, long p2) throws java.sql.SQLException {
		delegate.updateBinaryStream(p0, p1, p2);
	}

	@Override
	public void updateBinaryStream(int p0, java.io.InputStream p1) throws java.sql.SQLException {
		delegate.updateBinaryStream(p0, p1);
	}

	@Override
	public void updateBlob(String p0, java.io.InputStream p1, long p2) throws java.sql.SQLException {
		delegate.updateBlob(p0, p1, p2);
	}

	@Override
	public void updateBlob(String p0, java.io.InputStream p1) throws java.sql.SQLException {
		delegate.updateBlob(p0, p1);
	}

	@Override
	public void updateBlob(String p0, java.sql.Blob p1) throws java.sql.SQLException {
		delegate.updateBlob(p0, p1);
	}

	@Override
	public void updateBlob(int p0, java.io.InputStream p1, long p2) throws java.sql.SQLException {
		delegate.updateBlob(p0, p1, p2);
	}

	@Override
	public void updateBlob(int p0, java.io.InputStream p1) throws java.sql.SQLException {
		delegate.updateBlob(p0, p1);
	}

	@Override
	public void updateBlob(int p0, java.sql.Blob p1) throws java.sql.SQLException {
		delegate.updateBlob(p0, p1);
	}

	@Override
	public void updateBoolean(String p0, boolean p1) throws java.sql.SQLException {
		delegate.updateBoolean(p0, p1);
	}

	@Override
	public void updateBoolean(int p0, boolean p1) throws java.sql.SQLException {
		delegate.updateBoolean(p0, p1);
	}

	@Override
	public void updateByte(String p0, byte p1) throws java.sql.SQLException {
		delegate.updateByte(p0, p1);
	}

	@Override
	public void updateByte(int p0, byte p1) throws java.sql.SQLException {
		delegate.updateByte(p0, p1);
	}

	@Override
	public void updateBytes(String p0, byte[] p1) throws java.sql.SQLException {
		delegate.updateBytes(p0, p1);
	}

	@Override
	public void updateBytes(int p0, byte[] p1) throws java.sql.SQLException {
		delegate.updateBytes(p0, p1);
	}

	@Override
	public void updateCharacterStream(String p0, java.io.Reader p1, int p2) throws java.sql.SQLException {
		delegate.updateCharacterStream(p0, p1, p2);
	}

	@Override
	public void updateCharacterStream(String p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		delegate.updateCharacterStream(p0, p1, p2);
	}

	@Override
	public void updateCharacterStream(String p0, java.io.Reader p1) throws java.sql.SQLException {
		delegate.updateCharacterStream(p0, p1);
	}

	@Override
	public void updateCharacterStream(int p0, java.io.Reader p1, int p2) throws java.sql.SQLException {
		delegate.updateCharacterStream(p0, p1, p2);
	}

	@Override
	public void updateCharacterStream(int p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		delegate.updateCharacterStream(p0, p1, p2);
	}

	@Override
	public void updateCharacterStream(int p0, java.io.Reader p1) throws java.sql.SQLException {
		delegate.updateCharacterStream(p0, p1);
	}

	@Override
	public void updateClob(String p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		delegate.updateClob(p0, p1, p2);
	}

	@Override
	public void updateClob(String p0, java.io.Reader p1) throws java.sql.SQLException {
		delegate.updateClob(p0, p1);
	}

	@Override
	public void updateClob(String p0, java.sql.Clob p1) throws java.sql.SQLException {
		delegate.updateClob(p0, p1);
	}

	@Override
	public void updateClob(int p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		delegate.updateClob(p0, p1, p2);
	}

	@Override
	public void updateClob(int p0, java.io.Reader p1) throws java.sql.SQLException {
		delegate.updateClob(p0, p1);
	}

	@Override
	public void updateClob(int p0, java.sql.Clob p1) throws java.sql.SQLException {
		delegate.updateClob(p0, p1);
	}

	@Override
	public void updateDate(String p0, java.sql.Date p1) throws java.sql.SQLException {
		delegate.updateDate(p0, p1);
	}

	@Override
	public void updateDate(int p0, java.sql.Date p1) throws java.sql.SQLException {
		delegate.updateDate(p0, p1);
	}

	@Override
	public void updateDouble(String p0, double p1) throws java.sql.SQLException {
		delegate.updateDouble(p0, p1);
	}

	@Override
	public void updateDouble(int p0, double p1) throws java.sql.SQLException {
		delegate.updateDouble(p0, p1);
	}

	@Override
	public void updateFloat(String p0, float p1) throws java.sql.SQLException {
		delegate.updateFloat(p0, p1);
	}

	@Override
	public void updateFloat(int p0, float p1) throws java.sql.SQLException {
		delegate.updateFloat(p0, p1);
	}

	@Override
	public void updateInt(String p0, int p1) throws java.sql.SQLException {
		delegate.updateInt(p0, p1);
	}

	@Override
	public void updateInt(int p0, int p1) throws java.sql.SQLException {
		delegate.updateInt(p0, p1);
	}

	@Override
	public void updateLong(String p0, long p1) throws java.sql.SQLException {
		delegate.updateLong(p0, p1);
	}

	@Override
	public void updateLong(int p0, long p1) throws java.sql.SQLException {
		delegate.updateLong(p0, p1);
	}

	@Override
	public void updateNCharacterStream(String p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		delegate.updateNCharacterStream(p0, p1, p2);
	}

	@Override
	public void updateNCharacterStream(String p0, java.io.Reader p1) throws java.sql.SQLException {
		delegate.updateNCharacterStream(p0, p1);
	}

	@Override
	public void updateNCharacterStream(int p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		delegate.updateNCharacterStream(p0, p1, p2);
	}

	@Override
	public void updateNCharacterStream(int p0, java.io.Reader p1) throws java.sql.SQLException {
		delegate.updateNCharacterStream(p0, p1);
	}

	@Override
	public void updateNClob(String p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		delegate.updateNClob(p0, p1, p2);
	}

	@Override
	public void updateNClob(String p0, java.io.Reader p1) throws java.sql.SQLException {
		delegate.updateNClob(p0, p1);
	}

	@Override
	public void updateNClob(String p0, java.sql.NClob p1) throws java.sql.SQLException {
		delegate.updateNClob(p0, p1);
	}

	@Override
	public void updateNClob(int p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		delegate.updateNClob(p0, p1, p2);
	}

	@Override
	public void updateNClob(int p0, java.io.Reader p1) throws java.sql.SQLException {
		delegate.updateNClob(p0, p1);
	}

	@Override
	public void updateNClob(int p0, java.sql.NClob p1) throws java.sql.SQLException {
		delegate.updateNClob(p0, p1);
	}

	@Override
	public void updateNString(String p0, String p1) throws java.sql.SQLException {
		delegate.updateNString(p0, p1);
	}

	@Override
	public void updateNString(int p0, String p1) throws java.sql.SQLException {
		delegate.updateNString(p0, p1);
	}

	@Override
	public void updateNull(String p0) throws java.sql.SQLException {
		delegate.updateNull(p0);
	}

	@Override
	public void updateNull(int p0) throws java.sql.SQLException {
		delegate.updateNull(p0);
	}

	@Override
	public void updateObject(String p0, Object p1, int p2) throws java.sql.SQLException {
		delegate.updateObject(p0, p1, p2);
	}

	@Override
	public void updateObject(String p0, Object p1, java.sql.SQLType p2, int p3) throws java.sql.SQLException {
		delegate.updateObject(p0, p1, p2, p3);
	}

	@Override
	public void updateObject(String p0, Object p1, java.sql.SQLType p2) throws java.sql.SQLException {
		delegate.updateObject(p0, p1, p2);
	}

	@Override
	public void updateObject(String p0, Object p1) throws java.sql.SQLException {
		delegate.updateObject(p0, p1);
	}

	@Override
	public void updateObject(int p0, Object p1, int p2) throws java.sql.SQLException {
		delegate.updateObject(p0, p1, p2);
	}

	@Override
	public void updateObject(int p0, Object p1, java.sql.SQLType p2, int p3) throws java.sql.SQLException {
		delegate.updateObject(p0, p1, p2, p3);
	}

	@Override
	public void updateObject(int p0, Object p1, java.sql.SQLType p2) throws java.sql.SQLException {
		delegate.updateObject(p0, p1, p2);
	}

	@Override
	public void updateObject(int p0, Object p1) throws java.sql.SQLException {
		delegate.updateObject(p0, p1);
	}

	@Override
	public void updateRef(String p0, java.sql.Ref p1) throws java.sql.SQLException {
		delegate.updateRef(p0, p1);
	}

	@Override
	public void updateRef(int p0, java.sql.Ref p1) throws java.sql.SQLException {
		delegate.updateRef(p0, p1);
	}

	@Override
	public void updateRow() throws java.sql.SQLException {
		delegate.updateRow();
	}

	@Override
	public void updateRowId(String p0, java.sql.RowId p1) throws java.sql.SQLException {
		delegate.updateRowId(p0, p1);
	}

	@Override
	public void updateRowId(int p0, java.sql.RowId p1) throws java.sql.SQLException {
		delegate.updateRowId(p0, p1);
	}

	@Override
	public void updateSQLXML(String p0, java.sql.SQLXML p1) throws java.sql.SQLException {
		delegate.updateSQLXML(p0, p1);
	}

	@Override
	public void updateSQLXML(int p0, java.sql.SQLXML p1) throws java.sql.SQLException {
		delegate.updateSQLXML(p0, p1);
	}

	@Override
	public void updateShort(String p0, short p1) throws java.sql.SQLException {
		delegate.updateShort(p0, p1);
	}

	@Override
	public void updateShort(int p0, short p1) throws java.sql.SQLException {
		delegate.updateShort(p0, p1);
	}

	@Override
	public void updateString(String p0, String p1) throws java.sql.SQLException {
		delegate.updateString(p0, p1);
	}

	@Override
	public void updateString(int p0, String p1) throws java.sql.SQLException {
		delegate.updateString(p0, p1);
	}

	@Override
	public void updateTime(String p0, java.sql.Time p1) throws java.sql.SQLException {
		delegate.updateTime(p0, p1);
	}

	@Override
	public void updateTime(int p0, java.sql.Time p1) throws java.sql.SQLException {
		delegate.updateTime(p0, p1);
	}

	@Override
	public void updateTimestamp(String p0, java.sql.Timestamp p1) throws java.sql.SQLException {
		delegate.updateTimestamp(p0, p1);
	}

	@Override
	public void updateTimestamp(int p0, java.sql.Timestamp p1) throws java.sql.SQLException {
		delegate.updateTimestamp(p0, p1);
	}

	@Override
	public boolean wasNull() throws java.sql.SQLException {
		return delegate.wasNull();
	}
}
