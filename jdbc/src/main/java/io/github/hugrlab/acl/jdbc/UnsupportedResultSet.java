package io.github.hugrlab.acl.jdbc;

// GENERATED from the JDK's java.sql interfaces (spec 006): every method refuses with SQLFeatureNotSupportedException.
// Regenerate rather than edit; the behaviour lives in the subclasses.

@SuppressWarnings({"deprecation", "unused"})
abstract class UnsupportedResultSet implements java.sql.ResultSet {
	protected static java.sql.SQLFeatureNotSupportedException unsupported(String what) {
		return new java.sql.SQLFeatureNotSupportedException(what + " is not supported on a metadata result");
	}

	@Override
	public boolean absolute(int p0) throws java.sql.SQLException {
		throw unsupported("absolute");
	}

	@Override
	public void afterLast() throws java.sql.SQLException {
		throw unsupported("afterLast");
	}

	@Override
	public void beforeFirst() throws java.sql.SQLException {
		throw unsupported("beforeFirst");
	}

	@Override
	public void cancelRowUpdates() throws java.sql.SQLException {
		throw unsupported("cancelRowUpdates");
	}

	@Override
	public void clearWarnings() throws java.sql.SQLException {
		throw unsupported("clearWarnings");
	}

	@Override
	public void close() throws java.sql.SQLException {
		throw unsupported("close");
	}

	@Override
	public void deleteRow() throws java.sql.SQLException {
		throw unsupported("deleteRow");
	}

	@Override
	public int findColumn(String p0) throws java.sql.SQLException {
		throw unsupported("findColumn");
	}

	@Override
	public boolean first() throws java.sql.SQLException {
		throw unsupported("first");
	}

	@Override
	public java.sql.Array getArray(String p0) throws java.sql.SQLException {
		throw unsupported("getArray");
	}

	@Override
	public java.sql.Array getArray(int p0) throws java.sql.SQLException {
		throw unsupported("getArray");
	}

	@Override
	public java.io.InputStream getAsciiStream(String p0) throws java.sql.SQLException {
		throw unsupported("getAsciiStream");
	}

	@Override
	public java.io.InputStream getAsciiStream(int p0) throws java.sql.SQLException {
		throw unsupported("getAsciiStream");
	}

	@Override
	public java.math.BigDecimal getBigDecimal(String p0, int p1) throws java.sql.SQLException {
		throw unsupported("getBigDecimal");
	}

	@Override
	public java.math.BigDecimal getBigDecimal(String p0) throws java.sql.SQLException {
		throw unsupported("getBigDecimal");
	}

	@Override
	public java.math.BigDecimal getBigDecimal(int p0, int p1) throws java.sql.SQLException {
		throw unsupported("getBigDecimal");
	}

	@Override
	public java.math.BigDecimal getBigDecimal(int p0) throws java.sql.SQLException {
		throw unsupported("getBigDecimal");
	}

	@Override
	public java.io.InputStream getBinaryStream(String p0) throws java.sql.SQLException {
		throw unsupported("getBinaryStream");
	}

	@Override
	public java.io.InputStream getBinaryStream(int p0) throws java.sql.SQLException {
		throw unsupported("getBinaryStream");
	}

	@Override
	public java.sql.Blob getBlob(String p0) throws java.sql.SQLException {
		throw unsupported("getBlob");
	}

	@Override
	public java.sql.Blob getBlob(int p0) throws java.sql.SQLException {
		throw unsupported("getBlob");
	}

	@Override
	public boolean getBoolean(String p0) throws java.sql.SQLException {
		throw unsupported("getBoolean");
	}

	@Override
	public boolean getBoolean(int p0) throws java.sql.SQLException {
		throw unsupported("getBoolean");
	}

	@Override
	public byte getByte(String p0) throws java.sql.SQLException {
		throw unsupported("getByte");
	}

	@Override
	public byte getByte(int p0) throws java.sql.SQLException {
		throw unsupported("getByte");
	}

	@Override
	public byte[] getBytes(String p0) throws java.sql.SQLException {
		throw unsupported("getBytes");
	}

	@Override
	public byte[] getBytes(int p0) throws java.sql.SQLException {
		throw unsupported("getBytes");
	}

	@Override
	public java.io.Reader getCharacterStream(String p0) throws java.sql.SQLException {
		throw unsupported("getCharacterStream");
	}

	@Override
	public java.io.Reader getCharacterStream(int p0) throws java.sql.SQLException {
		throw unsupported("getCharacterStream");
	}

	@Override
	public java.sql.Clob getClob(String p0) throws java.sql.SQLException {
		throw unsupported("getClob");
	}

	@Override
	public java.sql.Clob getClob(int p0) throws java.sql.SQLException {
		throw unsupported("getClob");
	}

	@Override
	public int getConcurrency() throws java.sql.SQLException {
		throw unsupported("getConcurrency");
	}

	@Override
	public String getCursorName() throws java.sql.SQLException {
		throw unsupported("getCursorName");
	}

	@Override
	public java.sql.Date getDate(String p0, java.util.Calendar p1) throws java.sql.SQLException {
		throw unsupported("getDate");
	}

	@Override
	public java.sql.Date getDate(String p0) throws java.sql.SQLException {
		throw unsupported("getDate");
	}

	@Override
	public java.sql.Date getDate(int p0, java.util.Calendar p1) throws java.sql.SQLException {
		throw unsupported("getDate");
	}

	@Override
	public java.sql.Date getDate(int p0) throws java.sql.SQLException {
		throw unsupported("getDate");
	}

	@Override
	public double getDouble(String p0) throws java.sql.SQLException {
		throw unsupported("getDouble");
	}

	@Override
	public double getDouble(int p0) throws java.sql.SQLException {
		throw unsupported("getDouble");
	}

	@Override
	public int getFetchDirection() throws java.sql.SQLException {
		throw unsupported("getFetchDirection");
	}

	@Override
	public int getFetchSize() throws java.sql.SQLException {
		throw unsupported("getFetchSize");
	}

	@Override
	public float getFloat(String p0) throws java.sql.SQLException {
		throw unsupported("getFloat");
	}

	@Override
	public float getFloat(int p0) throws java.sql.SQLException {
		throw unsupported("getFloat");
	}

	@Override
	public int getHoldability() throws java.sql.SQLException {
		throw unsupported("getHoldability");
	}

	@Override
	public int getInt(String p0) throws java.sql.SQLException {
		throw unsupported("getInt");
	}

	@Override
	public int getInt(int p0) throws java.sql.SQLException {
		throw unsupported("getInt");
	}

	@Override
	public long getLong(String p0) throws java.sql.SQLException {
		throw unsupported("getLong");
	}

	@Override
	public long getLong(int p0) throws java.sql.SQLException {
		throw unsupported("getLong");
	}

	@Override
	public java.sql.ResultSetMetaData getMetaData() throws java.sql.SQLException {
		throw unsupported("getMetaData");
	}

	@Override
	public java.io.Reader getNCharacterStream(String p0) throws java.sql.SQLException {
		throw unsupported("getNCharacterStream");
	}

	@Override
	public java.io.Reader getNCharacterStream(int p0) throws java.sql.SQLException {
		throw unsupported("getNCharacterStream");
	}

	@Override
	public java.sql.NClob getNClob(String p0) throws java.sql.SQLException {
		throw unsupported("getNClob");
	}

	@Override
	public java.sql.NClob getNClob(int p0) throws java.sql.SQLException {
		throw unsupported("getNClob");
	}

	@Override
	public String getNString(String p0) throws java.sql.SQLException {
		throw unsupported("getNString");
	}

	@Override
	public String getNString(int p0) throws java.sql.SQLException {
		throw unsupported("getNString");
	}

	@Override
	public <T> T getObject(String p0, Class<T> p1) throws java.sql.SQLException {
		throw unsupported("getObject");
	}

	@Override
	public Object getObject(String p0, java.util.Map<String, Class<?>> p1) throws java.sql.SQLException {
		throw unsupported("getObject");
	}

	@Override
	public Object getObject(String p0) throws java.sql.SQLException {
		throw unsupported("getObject");
	}

	@Override
	public <T> T getObject(int p0, Class<T> p1) throws java.sql.SQLException {
		throw unsupported("getObject");
	}

	@Override
	public Object getObject(int p0, java.util.Map<String, Class<?>> p1) throws java.sql.SQLException {
		throw unsupported("getObject");
	}

	@Override
	public Object getObject(int p0) throws java.sql.SQLException {
		throw unsupported("getObject");
	}

	@Override
	public java.sql.Ref getRef(String p0) throws java.sql.SQLException {
		throw unsupported("getRef");
	}

	@Override
	public java.sql.Ref getRef(int p0) throws java.sql.SQLException {
		throw unsupported("getRef");
	}

	@Override
	public int getRow() throws java.sql.SQLException {
		throw unsupported("getRow");
	}

	@Override
	public java.sql.RowId getRowId(String p0) throws java.sql.SQLException {
		throw unsupported("getRowId");
	}

	@Override
	public java.sql.RowId getRowId(int p0) throws java.sql.SQLException {
		throw unsupported("getRowId");
	}

	@Override
	public java.sql.SQLXML getSQLXML(String p0) throws java.sql.SQLException {
		throw unsupported("getSQLXML");
	}

	@Override
	public java.sql.SQLXML getSQLXML(int p0) throws java.sql.SQLException {
		throw unsupported("getSQLXML");
	}

	@Override
	public short getShort(String p0) throws java.sql.SQLException {
		throw unsupported("getShort");
	}

	@Override
	public short getShort(int p0) throws java.sql.SQLException {
		throw unsupported("getShort");
	}

	@Override
	public java.sql.Statement getStatement() throws java.sql.SQLException {
		throw unsupported("getStatement");
	}

	@Override
	public String getString(String p0) throws java.sql.SQLException {
		throw unsupported("getString");
	}

	@Override
	public String getString(int p0) throws java.sql.SQLException {
		throw unsupported("getString");
	}

	@Override
	public java.sql.Time getTime(String p0, java.util.Calendar p1) throws java.sql.SQLException {
		throw unsupported("getTime");
	}

	@Override
	public java.sql.Time getTime(String p0) throws java.sql.SQLException {
		throw unsupported("getTime");
	}

	@Override
	public java.sql.Time getTime(int p0, java.util.Calendar p1) throws java.sql.SQLException {
		throw unsupported("getTime");
	}

	@Override
	public java.sql.Time getTime(int p0) throws java.sql.SQLException {
		throw unsupported("getTime");
	}

	@Override
	public java.sql.Timestamp getTimestamp(String p0, java.util.Calendar p1) throws java.sql.SQLException {
		throw unsupported("getTimestamp");
	}

	@Override
	public java.sql.Timestamp getTimestamp(String p0) throws java.sql.SQLException {
		throw unsupported("getTimestamp");
	}

	@Override
	public java.sql.Timestamp getTimestamp(int p0, java.util.Calendar p1) throws java.sql.SQLException {
		throw unsupported("getTimestamp");
	}

	@Override
	public java.sql.Timestamp getTimestamp(int p0) throws java.sql.SQLException {
		throw unsupported("getTimestamp");
	}

	@Override
	public int getType() throws java.sql.SQLException {
		throw unsupported("getType");
	}

	@Override
	public java.net.URL getURL(String p0) throws java.sql.SQLException {
		throw unsupported("getURL");
	}

	@Override
	public java.net.URL getURL(int p0) throws java.sql.SQLException {
		throw unsupported("getURL");
	}

	@Override
	public java.io.InputStream getUnicodeStream(String p0) throws java.sql.SQLException {
		throw unsupported("getUnicodeStream");
	}

	@Override
	public java.io.InputStream getUnicodeStream(int p0) throws java.sql.SQLException {
		throw unsupported("getUnicodeStream");
	}

	@Override
	public java.sql.SQLWarning getWarnings() throws java.sql.SQLException {
		throw unsupported("getWarnings");
	}

	@Override
	public void insertRow() throws java.sql.SQLException {
		throw unsupported("insertRow");
	}

	@Override
	public boolean isAfterLast() throws java.sql.SQLException {
		throw unsupported("isAfterLast");
	}

	@Override
	public boolean isBeforeFirst() throws java.sql.SQLException {
		throw unsupported("isBeforeFirst");
	}

	@Override
	public boolean isClosed() throws java.sql.SQLException {
		throw unsupported("isClosed");
	}

	@Override
	public boolean isFirst() throws java.sql.SQLException {
		throw unsupported("isFirst");
	}

	@Override
	public boolean isLast() throws java.sql.SQLException {
		throw unsupported("isLast");
	}

	@Override
	public boolean isWrapperFor(Class<?> p0) throws java.sql.SQLException {
		throw unsupported("isWrapperFor");
	}

	@Override
	public boolean last() throws java.sql.SQLException {
		throw unsupported("last");
	}

	@Override
	public void moveToCurrentRow() throws java.sql.SQLException {
		throw unsupported("moveToCurrentRow");
	}

	@Override
	public void moveToInsertRow() throws java.sql.SQLException {
		throw unsupported("moveToInsertRow");
	}

	@Override
	public boolean next() throws java.sql.SQLException {
		throw unsupported("next");
	}

	@Override
	public boolean previous() throws java.sql.SQLException {
		throw unsupported("previous");
	}

	@Override
	public void refreshRow() throws java.sql.SQLException {
		throw unsupported("refreshRow");
	}

	@Override
	public boolean relative(int p0) throws java.sql.SQLException {
		throw unsupported("relative");
	}

	@Override
	public boolean rowDeleted() throws java.sql.SQLException {
		throw unsupported("rowDeleted");
	}

	@Override
	public boolean rowInserted() throws java.sql.SQLException {
		throw unsupported("rowInserted");
	}

	@Override
	public boolean rowUpdated() throws java.sql.SQLException {
		throw unsupported("rowUpdated");
	}

	@Override
	public void setFetchDirection(int p0) throws java.sql.SQLException {
		throw unsupported("setFetchDirection");
	}

	@Override
	public void setFetchSize(int p0) throws java.sql.SQLException {
		throw unsupported("setFetchSize");
	}

	@Override
	public <T> T unwrap(Class<T> p0) throws java.sql.SQLException {
		throw unsupported("unwrap");
	}

	@Override
	public void updateArray(String p0, java.sql.Array p1) throws java.sql.SQLException {
		throw unsupported("updateArray");
	}

	@Override
	public void updateArray(int p0, java.sql.Array p1) throws java.sql.SQLException {
		throw unsupported("updateArray");
	}

	@Override
	public void updateAsciiStream(String p0, java.io.InputStream p1, int p2) throws java.sql.SQLException {
		throw unsupported("updateAsciiStream");
	}

	@Override
	public void updateAsciiStream(String p0, java.io.InputStream p1, long p2) throws java.sql.SQLException {
		throw unsupported("updateAsciiStream");
	}

	@Override
	public void updateAsciiStream(String p0, java.io.InputStream p1) throws java.sql.SQLException {
		throw unsupported("updateAsciiStream");
	}

	@Override
	public void updateAsciiStream(int p0, java.io.InputStream p1, int p2) throws java.sql.SQLException {
		throw unsupported("updateAsciiStream");
	}

	@Override
	public void updateAsciiStream(int p0, java.io.InputStream p1, long p2) throws java.sql.SQLException {
		throw unsupported("updateAsciiStream");
	}

	@Override
	public void updateAsciiStream(int p0, java.io.InputStream p1) throws java.sql.SQLException {
		throw unsupported("updateAsciiStream");
	}

	@Override
	public void updateBigDecimal(String p0, java.math.BigDecimal p1) throws java.sql.SQLException {
		throw unsupported("updateBigDecimal");
	}

	@Override
	public void updateBigDecimal(int p0, java.math.BigDecimal p1) throws java.sql.SQLException {
		throw unsupported("updateBigDecimal");
	}

	@Override
	public void updateBinaryStream(String p0, java.io.InputStream p1, int p2) throws java.sql.SQLException {
		throw unsupported("updateBinaryStream");
	}

	@Override
	public void updateBinaryStream(String p0, java.io.InputStream p1, long p2) throws java.sql.SQLException {
		throw unsupported("updateBinaryStream");
	}

	@Override
	public void updateBinaryStream(String p0, java.io.InputStream p1) throws java.sql.SQLException {
		throw unsupported("updateBinaryStream");
	}

	@Override
	public void updateBinaryStream(int p0, java.io.InputStream p1, int p2) throws java.sql.SQLException {
		throw unsupported("updateBinaryStream");
	}

	@Override
	public void updateBinaryStream(int p0, java.io.InputStream p1, long p2) throws java.sql.SQLException {
		throw unsupported("updateBinaryStream");
	}

	@Override
	public void updateBinaryStream(int p0, java.io.InputStream p1) throws java.sql.SQLException {
		throw unsupported("updateBinaryStream");
	}

	@Override
	public void updateBlob(String p0, java.io.InputStream p1, long p2) throws java.sql.SQLException {
		throw unsupported("updateBlob");
	}

	@Override
	public void updateBlob(String p0, java.io.InputStream p1) throws java.sql.SQLException {
		throw unsupported("updateBlob");
	}

	@Override
	public void updateBlob(String p0, java.sql.Blob p1) throws java.sql.SQLException {
		throw unsupported("updateBlob");
	}

	@Override
	public void updateBlob(int p0, java.io.InputStream p1, long p2) throws java.sql.SQLException {
		throw unsupported("updateBlob");
	}

	@Override
	public void updateBlob(int p0, java.io.InputStream p1) throws java.sql.SQLException {
		throw unsupported("updateBlob");
	}

	@Override
	public void updateBlob(int p0, java.sql.Blob p1) throws java.sql.SQLException {
		throw unsupported("updateBlob");
	}

	@Override
	public void updateBoolean(String p0, boolean p1) throws java.sql.SQLException {
		throw unsupported("updateBoolean");
	}

	@Override
	public void updateBoolean(int p0, boolean p1) throws java.sql.SQLException {
		throw unsupported("updateBoolean");
	}

	@Override
	public void updateByte(String p0, byte p1) throws java.sql.SQLException {
		throw unsupported("updateByte");
	}

	@Override
	public void updateByte(int p0, byte p1) throws java.sql.SQLException {
		throw unsupported("updateByte");
	}

	@Override
	public void updateBytes(String p0, byte[] p1) throws java.sql.SQLException {
		throw unsupported("updateBytes");
	}

	@Override
	public void updateBytes(int p0, byte[] p1) throws java.sql.SQLException {
		throw unsupported("updateBytes");
	}

	@Override
	public void updateCharacterStream(String p0, java.io.Reader p1, int p2) throws java.sql.SQLException {
		throw unsupported("updateCharacterStream");
	}

	@Override
	public void updateCharacterStream(String p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		throw unsupported("updateCharacterStream");
	}

	@Override
	public void updateCharacterStream(String p0, java.io.Reader p1) throws java.sql.SQLException {
		throw unsupported("updateCharacterStream");
	}

	@Override
	public void updateCharacterStream(int p0, java.io.Reader p1, int p2) throws java.sql.SQLException {
		throw unsupported("updateCharacterStream");
	}

	@Override
	public void updateCharacterStream(int p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		throw unsupported("updateCharacterStream");
	}

	@Override
	public void updateCharacterStream(int p0, java.io.Reader p1) throws java.sql.SQLException {
		throw unsupported("updateCharacterStream");
	}

	@Override
	public void updateClob(String p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		throw unsupported("updateClob");
	}

	@Override
	public void updateClob(String p0, java.io.Reader p1) throws java.sql.SQLException {
		throw unsupported("updateClob");
	}

	@Override
	public void updateClob(String p0, java.sql.Clob p1) throws java.sql.SQLException {
		throw unsupported("updateClob");
	}

	@Override
	public void updateClob(int p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		throw unsupported("updateClob");
	}

	@Override
	public void updateClob(int p0, java.io.Reader p1) throws java.sql.SQLException {
		throw unsupported("updateClob");
	}

	@Override
	public void updateClob(int p0, java.sql.Clob p1) throws java.sql.SQLException {
		throw unsupported("updateClob");
	}

	@Override
	public void updateDate(String p0, java.sql.Date p1) throws java.sql.SQLException {
		throw unsupported("updateDate");
	}

	@Override
	public void updateDate(int p0, java.sql.Date p1) throws java.sql.SQLException {
		throw unsupported("updateDate");
	}

	@Override
	public void updateDouble(String p0, double p1) throws java.sql.SQLException {
		throw unsupported("updateDouble");
	}

	@Override
	public void updateDouble(int p0, double p1) throws java.sql.SQLException {
		throw unsupported("updateDouble");
	}

	@Override
	public void updateFloat(String p0, float p1) throws java.sql.SQLException {
		throw unsupported("updateFloat");
	}

	@Override
	public void updateFloat(int p0, float p1) throws java.sql.SQLException {
		throw unsupported("updateFloat");
	}

	@Override
	public void updateInt(String p0, int p1) throws java.sql.SQLException {
		throw unsupported("updateInt");
	}

	@Override
	public void updateInt(int p0, int p1) throws java.sql.SQLException {
		throw unsupported("updateInt");
	}

	@Override
	public void updateLong(String p0, long p1) throws java.sql.SQLException {
		throw unsupported("updateLong");
	}

	@Override
	public void updateLong(int p0, long p1) throws java.sql.SQLException {
		throw unsupported("updateLong");
	}

	@Override
	public void updateNCharacterStream(String p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		throw unsupported("updateNCharacterStream");
	}

	@Override
	public void updateNCharacterStream(String p0, java.io.Reader p1) throws java.sql.SQLException {
		throw unsupported("updateNCharacterStream");
	}

	@Override
	public void updateNCharacterStream(int p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		throw unsupported("updateNCharacterStream");
	}

	@Override
	public void updateNCharacterStream(int p0, java.io.Reader p1) throws java.sql.SQLException {
		throw unsupported("updateNCharacterStream");
	}

	@Override
	public void updateNClob(String p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		throw unsupported("updateNClob");
	}

	@Override
	public void updateNClob(String p0, java.io.Reader p1) throws java.sql.SQLException {
		throw unsupported("updateNClob");
	}

	@Override
	public void updateNClob(String p0, java.sql.NClob p1) throws java.sql.SQLException {
		throw unsupported("updateNClob");
	}

	@Override
	public void updateNClob(int p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
		throw unsupported("updateNClob");
	}

	@Override
	public void updateNClob(int p0, java.io.Reader p1) throws java.sql.SQLException {
		throw unsupported("updateNClob");
	}

	@Override
	public void updateNClob(int p0, java.sql.NClob p1) throws java.sql.SQLException {
		throw unsupported("updateNClob");
	}

	@Override
	public void updateNString(String p0, String p1) throws java.sql.SQLException {
		throw unsupported("updateNString");
	}

	@Override
	public void updateNString(int p0, String p1) throws java.sql.SQLException {
		throw unsupported("updateNString");
	}

	@Override
	public void updateNull(String p0) throws java.sql.SQLException {
		throw unsupported("updateNull");
	}

	@Override
	public void updateNull(int p0) throws java.sql.SQLException {
		throw unsupported("updateNull");
	}

	@Override
	public void updateObject(String p0, Object p1, int p2) throws java.sql.SQLException {
		throw unsupported("updateObject");
	}

	@Override
	public void updateObject(String p0, Object p1) throws java.sql.SQLException {
		throw unsupported("updateObject");
	}

	@Override
	public void updateObject(int p0, Object p1, int p2) throws java.sql.SQLException {
		throw unsupported("updateObject");
	}

	@Override
	public void updateObject(int p0, Object p1) throws java.sql.SQLException {
		throw unsupported("updateObject");
	}

	@Override
	public void updateRef(String p0, java.sql.Ref p1) throws java.sql.SQLException {
		throw unsupported("updateRef");
	}

	@Override
	public void updateRef(int p0, java.sql.Ref p1) throws java.sql.SQLException {
		throw unsupported("updateRef");
	}

	@Override
	public void updateRow() throws java.sql.SQLException {
		throw unsupported("updateRow");
	}

	@Override
	public void updateRowId(String p0, java.sql.RowId p1) throws java.sql.SQLException {
		throw unsupported("updateRowId");
	}

	@Override
	public void updateRowId(int p0, java.sql.RowId p1) throws java.sql.SQLException {
		throw unsupported("updateRowId");
	}

	@Override
	public void updateSQLXML(String p0, java.sql.SQLXML p1) throws java.sql.SQLException {
		throw unsupported("updateSQLXML");
	}

	@Override
	public void updateSQLXML(int p0, java.sql.SQLXML p1) throws java.sql.SQLException {
		throw unsupported("updateSQLXML");
	}

	@Override
	public void updateShort(String p0, short p1) throws java.sql.SQLException {
		throw unsupported("updateShort");
	}

	@Override
	public void updateShort(int p0, short p1) throws java.sql.SQLException {
		throw unsupported("updateShort");
	}

	@Override
	public void updateString(String p0, String p1) throws java.sql.SQLException {
		throw unsupported("updateString");
	}

	@Override
	public void updateString(int p0, String p1) throws java.sql.SQLException {
		throw unsupported("updateString");
	}

	@Override
	public void updateTime(String p0, java.sql.Time p1) throws java.sql.SQLException {
		throw unsupported("updateTime");
	}

	@Override
	public void updateTime(int p0, java.sql.Time p1) throws java.sql.SQLException {
		throw unsupported("updateTime");
	}

	@Override
	public void updateTimestamp(String p0, java.sql.Timestamp p1) throws java.sql.SQLException {
		throw unsupported("updateTimestamp");
	}

	@Override
	public void updateTimestamp(int p0, java.sql.Timestamp p1) throws java.sql.SQLException {
		throw unsupported("updateTimestamp");
	}

	@Override
	public boolean wasNull() throws java.sql.SQLException {
		throw unsupported("wasNull");
	}
}
