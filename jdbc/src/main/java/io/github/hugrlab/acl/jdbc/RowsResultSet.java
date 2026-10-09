package io.github.hugrlab.acl.jdbc;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A metadata answer held in memory: fixed columns and the rows. Its columns are those JDBC names for
 * the method whatever the rows, so an empty answer still has them (Arrow builds a metadata result's
 * columns from its first batch, and an empty stream has none - spec 006).
 */
final class RowsResultSet extends UnsupportedResultSet {
	/** One column: its JDBC name and type. */
	record ColumnSpec(String name, int jdbcType) {
		static ColumnSpec text(String name) {
			return new ColumnSpec(name, Types.VARCHAR);
		}

		static ColumnSpec integer(String name) {
			return new ColumnSpec(name, Types.INTEGER);
		}

		static ColumnSpec small(String name) {
			return new ColumnSpec(name, Types.SMALLINT);
		}

		static ColumnSpec bool(String name) {
			return new ColumnSpec(name, Types.BOOLEAN);
		}
	}

	private final List<ColumnSpec> columns;
	private final List<Object[]> rows;
	private int cursor = -1; // before the first row
	private boolean closed;
	private boolean wasNull;
	private int fetchSize;

	RowsResultSet(List<ColumnSpec> columns, List<Object[]> rows) {
		this.columns = List.copyOf(columns);
		this.rows = new ArrayList<>(rows.size());
		for (Object[] row : rows) {
			if (row.length != columns.size()) {
				throw new IllegalArgumentException("a row of " + row.length + " values for " + columns.size() + " columns");
			}
			this.rows.add(row.clone());
		}
	}

	List<ColumnSpec> columns() {
		return columns;
	}

	int size() {
		return rows.size();
	}

	private void checkOpen() throws SQLException {
		if (closed) {
			throw new SQLException("the result set is closed");
		}
	}

	private Object value(int column) throws SQLException {
		checkOpen();
		if (cursor < 0 || cursor >= rows.size()) {
			throw new SQLException("no current row");
		}
		if (column < 1 || column > columns.size()) {
			throw new SQLException("no column " + column + " (" + columns.size() + " columns)");
		}
		Object v = rows.get(cursor)[column - 1];
		wasNull = v == null;
		return v;
	}

	@Override
	public boolean next() throws SQLException {
		checkOpen();
		if (cursor < rows.size()) {
			cursor++;
		}
		return cursor < rows.size();
	}

	@Override
	public boolean previous() throws SQLException {
		checkOpen();
		if (cursor >= 0) {
			cursor--;
		}
		return cursor >= 0;
	}

	@Override
	public boolean absolute(int row) throws SQLException {
		checkOpen();
		cursor = row >= 0 ? row - 1 : rows.size() + row;
		cursor = Math.max(-1, Math.min(rows.size(), cursor));
		return cursor >= 0 && cursor < rows.size();
	}

	@Override
	public boolean relative(int rows) throws SQLException {
		checkOpen();
		cursor = Math.max(-1, Math.min(this.rows.size(), cursor + rows));
		return cursor >= 0 && cursor < this.rows.size();
	}

	@Override
	public boolean first() throws SQLException {
		return absolute(1);
	}

	@Override
	public boolean last() throws SQLException {
		return absolute(-1);
	}

	@Override
	public void beforeFirst() throws SQLException {
		checkOpen();
		cursor = -1;
	}

	@Override
	public void afterLast() throws SQLException {
		checkOpen();
		cursor = rows.size();
	}

	@Override
	public boolean isBeforeFirst() throws SQLException {
		checkOpen();
		return cursor < 0 && !rows.isEmpty();
	}

	@Override
	public boolean isAfterLast() throws SQLException {
		checkOpen();
		return cursor >= rows.size() && !rows.isEmpty();
	}

	@Override
	public boolean isFirst() throws SQLException {
		checkOpen();
		return cursor == 0 && !rows.isEmpty();
	}

	@Override
	public boolean isLast() throws SQLException {
		checkOpen();
		return cursor == rows.size() - 1 && !rows.isEmpty();
	}

	@Override
	public int getRow() throws SQLException {
		checkOpen();
		return cursor >= 0 && cursor < rows.size() ? cursor + 1 : 0;
	}

	@Override
	public void close() {
		closed = true;
	}

	@Override
	public boolean isClosed() {
		return closed;
	}

	@Override
	public boolean wasNull() {
		return wasNull;
	}

	@Override
	public int findColumn(String label) throws SQLException {
		for (int i = 0; i < columns.size(); i++) {
			if (columns.get(i).name().equalsIgnoreCase(label)) {
				return i + 1;
			}
		}
		throw new SQLException("no column " + label);
	}

	@Override
	public ResultSetMetaData getMetaData() {
		return new RowsResultSetMetaData(columns);
	}

	@Override
	public Object getObject(int column) throws SQLException {
		return value(column);
	}

	@Override
	public Object getObject(String label) throws SQLException {
		return getObject(findColumn(label));
	}

	@Override
	public <T> T getObject(int column, Class<T> type) throws SQLException {
		Object v = value(column);
		if (v == null) {
			return null;
		}
		if (type.isInstance(v)) {
			return type.cast(v);
		}
		if (type == String.class) {
			return type.cast(v.toString());
		}
		if (v instanceof Number n) {
			if (type == Integer.class) {
				return type.cast(n.intValue());
			}
			if (type == Long.class) {
				return type.cast(n.longValue());
			}
			if (type == Short.class) {
				return type.cast(n.shortValue());
			}
		}
		throw new SQLException("column " + column + " is not a " + type.getName());
	}

	@Override
	public <T> T getObject(String label, Class<T> type) throws SQLException {
		return getObject(findColumn(label), type);
	}

	@Override
	public String getString(int column) throws SQLException {
		Object v = value(column);
		return v == null ? null : v.toString();
	}

	@Override
	public String getString(String label) throws SQLException {
		return getString(findColumn(label));
	}

	@Override
	public boolean getBoolean(int column) throws SQLException {
		Object v = value(column);
		if (v == null) {
			return false;
		}
		if (v instanceof Boolean b) {
			return b;
		}
		if (v instanceof Number n) {
			return n.intValue() != 0;
		}
		String s = v.toString().trim().toLowerCase(Locale.ROOT);
		return s.equals("true") || s.equals("yes") || s.equals("1");
	}

	@Override
	public boolean getBoolean(String label) throws SQLException {
		return getBoolean(findColumn(label));
	}

	private Number number(int column) throws SQLException {
		Object v = value(column);
		if (v == null) {
			return 0;
		}
		if (v instanceof Number n) {
			return n;
		}
		if (v instanceof Boolean b) {
			return b ? 1 : 0;
		}
		try {
			return new BigDecimal(v.toString().trim());
		} catch (NumberFormatException e) {
			throw new SQLException("column " + column + " is not a number: " + v);
		}
	}

	@Override
	public byte getByte(int column) throws SQLException {
		return number(column).byteValue();
	}

	@Override
	public byte getByte(String label) throws SQLException {
		return getByte(findColumn(label));
	}

	@Override
	public short getShort(int column) throws SQLException {
		return number(column).shortValue();
	}

	@Override
	public short getShort(String label) throws SQLException {
		return getShort(findColumn(label));
	}

	@Override
	public int getInt(int column) throws SQLException {
		return number(column).intValue();
	}

	@Override
	public int getInt(String label) throws SQLException {
		return getInt(findColumn(label));
	}

	@Override
	public long getLong(int column) throws SQLException {
		return number(column).longValue();
	}

	@Override
	public long getLong(String label) throws SQLException {
		return getLong(findColumn(label));
	}

	@Override
	public float getFloat(int column) throws SQLException {
		return number(column).floatValue();
	}

	@Override
	public float getFloat(String label) throws SQLException {
		return getFloat(findColumn(label));
	}

	@Override
	public double getDouble(int column) throws SQLException {
		return number(column).doubleValue();
	}

	@Override
	public double getDouble(String label) throws SQLException {
		return getDouble(findColumn(label));
	}

	@Override
	public BigDecimal getBigDecimal(int column) throws SQLException {
		Object v = value(column);
		return v == null ? null : new BigDecimal(number(column).toString());
	}

	@Override
	public BigDecimal getBigDecimal(String label) throws SQLException {
		return getBigDecimal(findColumn(label));
	}

	@Override
	public Statement getStatement() {
		return null; // a metadata answer has no statement (JDBC allows null)
	}

	@Override
	public SQLWarning getWarnings() {
		return null;
	}

	@Override
	public void clearWarnings() {
	}

	@Override
	public int getType() {
		return ResultSet.TYPE_SCROLL_INSENSITIVE;
	}

	@Override
	public int getConcurrency() {
		return ResultSet.CONCUR_READ_ONLY;
	}

	@Override
	public int getHoldability() {
		return ResultSet.CLOSE_CURSORS_AT_COMMIT;
	}

	@Override
	public int getFetchDirection() {
		return ResultSet.FETCH_FORWARD;
	}

	@Override
	public void setFetchDirection(int direction) {
	}

	@Override
	public int getFetchSize() {
		return fetchSize;
	}

	@Override
	public void setFetchSize(int rows) {
		this.fetchSize = rows;
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		if (iface.isInstance(this)) {
			return iface.cast(this);
		}
		throw new SQLException("not a wrapper of " + iface.getName());
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) {
		return iface.isInstance(this);
	}

	/** The metadata of a {@link RowsResultSet}: its fixed columns. */
	static final class RowsResultSetMetaData implements ResultSetMetaData {
		private final List<ColumnSpec> columns;

		RowsResultSetMetaData(List<ColumnSpec> columns) {
			this.columns = columns;
		}

		private ColumnSpec column(int i) throws SQLException {
			if (i < 1 || i > columns.size()) {
				throw new SQLException("no column " + i);
			}
			return columns.get(i - 1);
		}

		@Override
		public int getColumnCount() {
			return columns.size();
		}

		@Override
		public boolean isAutoIncrement(int column) {
			return false;
		}

		@Override
		public boolean isCaseSensitive(int column) throws SQLException {
			return column(column).jdbcType() == Types.VARCHAR;
		}

		@Override
		public boolean isSearchable(int column) {
			return true;
		}

		@Override
		public boolean isCurrency(int column) {
			return false;
		}

		@Override
		public int isNullable(int column) {
			return columnNullableUnknown;
		}

		@Override
		public boolean isSigned(int column) throws SQLException {
			int t = column(column).jdbcType();
			return t == Types.INTEGER || t == Types.SMALLINT || t == Types.BIGINT;
		}

		@Override
		public int getColumnDisplaySize(int column) throws SQLException {
			return switch (column(column).jdbcType()) {
				case Types.BOOLEAN -> 5;
				case Types.SMALLINT -> 6;
				case Types.INTEGER -> 11;
				case Types.BIGINT -> 20;
				default -> 128;
			};
		}

		@Override
		public String getColumnLabel(int column) throws SQLException {
			return column(column).name();
		}

		@Override
		public String getColumnName(int column) throws SQLException {
			return column(column).name();
		}

		@Override
		public String getSchemaName(int column) {
			return "";
		}

		@Override
		public int getPrecision(int column) throws SQLException {
			return switch (column(column).jdbcType()) {
				case Types.SMALLINT -> 5;
				case Types.INTEGER -> 10;
				case Types.BIGINT -> 19;
				case Types.BOOLEAN -> 1;
				default -> 0;
			};
		}

		@Override
		public int getScale(int column) {
			return 0;
		}

		@Override
		public String getTableName(int column) {
			return "";
		}

		@Override
		public String getCatalogName(int column) {
			return "";
		}

		@Override
		public int getColumnType(int column) throws SQLException {
			return column(column).jdbcType();
		}

		@Override
		public String getColumnTypeName(int column) throws SQLException {
			return DuckTypes.jdbcName(column(column).jdbcType());
		}

		@Override
		public boolean isReadOnly(int column) {
			return true;
		}

		@Override
		public boolean isWritable(int column) {
			return false;
		}

		@Override
		public boolean isDefinitelyWritable(int column) {
			return false;
		}

		@Override
		public String getColumnClassName(int column) throws SQLException {
			return switch (column(column).jdbcType()) {
				case Types.SMALLINT -> Short.class.getName();
				case Types.INTEGER -> Integer.class.getName();
				case Types.BIGINT -> Long.class.getName();
				case Types.BOOLEAN -> Boolean.class.getName();
				default -> String.class.getName();
			};
		}

		@Override
		public <T> T unwrap(Class<T> iface) throws SQLException {
			if (iface.isInstance(this)) {
				return iface.cast(this);
			}
			throw new SQLException("not a wrapper of " + iface.getName());
		}

		@Override
		public boolean isWrapperFor(Class<?> iface) {
			return iface.isInstance(this);
		}
	}
}
