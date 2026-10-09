package io.github.hugrlab.acl.jdbc;

// GENERATED from the JDK's java.sql interfaces (spec 006): every method forwards to the delegate.
// Regenerate rather than edit; the behaviour lives in the subclasses.

@SuppressWarnings({"deprecation", "unused"})
abstract class ForwardingStatement<S extends java.sql.Statement> implements java.sql.Statement {
	protected final S delegate;

	protected ForwardingStatement(S delegate) {
		this.delegate = delegate;
	}

	@Override
	public void addBatch(String p0) throws java.sql.SQLException {
		delegate.addBatch(p0);
	}

	@Override
	public void cancel() throws java.sql.SQLException {
		delegate.cancel();
	}

	@Override
	public void clearBatch() throws java.sql.SQLException {
		delegate.clearBatch();
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
	public void closeOnCompletion() throws java.sql.SQLException {
		delegate.closeOnCompletion();
	}

	@Override
	public String enquoteIdentifier(String p0, boolean p1) throws java.sql.SQLException {
		return delegate.enquoteIdentifier(p0, p1);
	}

	@Override
	public String enquoteLiteral(String p0) throws java.sql.SQLException {
		return delegate.enquoteLiteral(p0);
	}

	@Override
	public String enquoteNCharLiteral(String p0) throws java.sql.SQLException {
		return delegate.enquoteNCharLiteral(p0);
	}

	@Override
	public boolean execute(String p0, int[] p1) throws java.sql.SQLException {
		return delegate.execute(p0, p1);
	}

	@Override
	public boolean execute(String p0, String[] p1) throws java.sql.SQLException {
		return delegate.execute(p0, p1);
	}

	@Override
	public boolean execute(String p0, int p1) throws java.sql.SQLException {
		return delegate.execute(p0, p1);
	}

	@Override
	public boolean execute(String p0) throws java.sql.SQLException {
		return delegate.execute(p0);
	}

	@Override
	public int[] executeBatch() throws java.sql.SQLException {
		return delegate.executeBatch();
	}

	@Override
	public long[] executeLargeBatch() throws java.sql.SQLException {
		return delegate.executeLargeBatch();
	}

	@Override
	public long executeLargeUpdate(String p0, int[] p1) throws java.sql.SQLException {
		return delegate.executeLargeUpdate(p0, p1);
	}

	@Override
	public long executeLargeUpdate(String p0, String[] p1) throws java.sql.SQLException {
		return delegate.executeLargeUpdate(p0, p1);
	}

	@Override
	public long executeLargeUpdate(String p0, int p1) throws java.sql.SQLException {
		return delegate.executeLargeUpdate(p0, p1);
	}

	@Override
	public long executeLargeUpdate(String p0) throws java.sql.SQLException {
		return delegate.executeLargeUpdate(p0);
	}

	@Override
	public java.sql.ResultSet executeQuery(String p0) throws java.sql.SQLException {
		return delegate.executeQuery(p0);
	}

	@Override
	public int executeUpdate(String p0, int[] p1) throws java.sql.SQLException {
		return delegate.executeUpdate(p0, p1);
	}

	@Override
	public int executeUpdate(String p0, String[] p1) throws java.sql.SQLException {
		return delegate.executeUpdate(p0, p1);
	}

	@Override
	public int executeUpdate(String p0, int p1) throws java.sql.SQLException {
		return delegate.executeUpdate(p0, p1);
	}

	@Override
	public int executeUpdate(String p0) throws java.sql.SQLException {
		return delegate.executeUpdate(p0);
	}

	@Override
	public java.sql.Connection getConnection() throws java.sql.SQLException {
		return delegate.getConnection();
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
	public java.sql.ResultSet getGeneratedKeys() throws java.sql.SQLException {
		return delegate.getGeneratedKeys();
	}

	@Override
	public long getLargeMaxRows() throws java.sql.SQLException {
		return delegate.getLargeMaxRows();
	}

	@Override
	public long getLargeUpdateCount() throws java.sql.SQLException {
		return delegate.getLargeUpdateCount();
	}

	@Override
	public int getMaxFieldSize() throws java.sql.SQLException {
		return delegate.getMaxFieldSize();
	}

	@Override
	public int getMaxRows() throws java.sql.SQLException {
		return delegate.getMaxRows();
	}

	@Override
	public boolean getMoreResults() throws java.sql.SQLException {
		return delegate.getMoreResults();
	}

	@Override
	public boolean getMoreResults(int p0) throws java.sql.SQLException {
		return delegate.getMoreResults(p0);
	}

	@Override
	public int getQueryTimeout() throws java.sql.SQLException {
		return delegate.getQueryTimeout();
	}

	@Override
	public java.sql.ResultSet getResultSet() throws java.sql.SQLException {
		return delegate.getResultSet();
	}

	@Override
	public int getResultSetConcurrency() throws java.sql.SQLException {
		return delegate.getResultSetConcurrency();
	}

	@Override
	public int getResultSetHoldability() throws java.sql.SQLException {
		return delegate.getResultSetHoldability();
	}

	@Override
	public int getResultSetType() throws java.sql.SQLException {
		return delegate.getResultSetType();
	}

	@Override
	public int getUpdateCount() throws java.sql.SQLException {
		return delegate.getUpdateCount();
	}

	@Override
	public java.sql.SQLWarning getWarnings() throws java.sql.SQLException {
		return delegate.getWarnings();
	}

	@Override
	public boolean isCloseOnCompletion() throws java.sql.SQLException {
		return delegate.isCloseOnCompletion();
	}

	@Override
	public boolean isClosed() throws java.sql.SQLException {
		return delegate.isClosed();
	}

	@Override
	public boolean isPoolable() throws java.sql.SQLException {
		return delegate.isPoolable();
	}

	@Override
	public boolean isSimpleIdentifier(String p0) throws java.sql.SQLException {
		return delegate.isSimpleIdentifier(p0);
	}

	@Override
	public boolean isWrapperFor(Class<?> p0) throws java.sql.SQLException {
		return delegate.isWrapperFor(p0);
	}

	@Override
	public void setCursorName(String p0) throws java.sql.SQLException {
		delegate.setCursorName(p0);
	}

	@Override
	public void setEscapeProcessing(boolean p0) throws java.sql.SQLException {
		delegate.setEscapeProcessing(p0);
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
	public void setLargeMaxRows(long p0) throws java.sql.SQLException {
		delegate.setLargeMaxRows(p0);
	}

	@Override
	public void setMaxFieldSize(int p0) throws java.sql.SQLException {
		delegate.setMaxFieldSize(p0);
	}

	@Override
	public void setMaxRows(int p0) throws java.sql.SQLException {
		delegate.setMaxRows(p0);
	}

	@Override
	public void setPoolable(boolean p0) throws java.sql.SQLException {
		delegate.setPoolable(p0);
	}

	@Override
	public void setQueryTimeout(int p0) throws java.sql.SQLException {
		delegate.setQueryTimeout(p0);
	}

	@Override
	public <T> T unwrap(Class<T> p0) throws java.sql.SQLException {
		return delegate.unwrap(p0);
	}
}
