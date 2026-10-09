package io.github.hugrlab.acl.jdbc;

import io.github.hugrlab.acl.jdbc.RowsResultSet.ColumnSpec;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A duckdb LIST / ARRAY value as {@link java.sql.Array}, its elements already JDBC objects: a STRUCT
 * element is an {@link AclStruct}, text a String (Arrow's own array hands its raw maps and
 * {@code Text}s). {@link #getResultSet()} is JDBC's two columns, INDEX (from 1) and VALUE.
 */
public final class AclArray implements Array {
	private final DuckTypes.Type element;
	private final Object[] elements;

	AclArray(DuckTypes.Type element, List<?> elements) {
		this.element = element;
		this.elements = elements.toArray();
	}

	@Override
	public String getBaseTypeName() {
		return element == null ? "UNKNOWN" : element.text();
	}

	@Override
	public int getBaseType() {
		return element == null ? Types.OTHER : element.jdbcType();
	}

	@Override
	public Object getArray() {
		return elements.clone();
	}

	@Override
	public Object getArray(Map<String, Class<?>> map) {
		return getArray();
	}

	@Override
	public Object getArray(long index, int count) throws SQLException {
		int from = slice(index, count);
		Object[] out = new Object[Math.min(count, elements.length - from)];
		System.arraycopy(elements, from, out, 0, out.length);
		return out;
	}

	@Override
	public Object getArray(long index, int count, Map<String, Class<?>> map) throws SQLException {
		return getArray(index, count);
	}

	@Override
	public ResultSet getResultSet() throws SQLException {
		return getResultSet(1, elements.length);
	}

	@Override
	public ResultSet getResultSet(Map<String, Class<?>> map) throws SQLException {
		return getResultSet();
	}

	@Override
	public ResultSet getResultSet(long index, int count) throws SQLException {
		int from = slice(index, count);
		List<Object[]> rows = new ArrayList<>();
		for (int i = from; i < elements.length && i < from + count; i++) {
			rows.add(new Object[] {(long) i + 1, elements[i]});
		}
		return new RowsResultSet(List.of(new ColumnSpec("INDEX", Types.BIGINT), new ColumnSpec("VALUE", getBaseType())),
		    rows);
	}

	@Override
	public ResultSet getResultSet(long index, int count, Map<String, Class<?>> map) throws SQLException {
		return getResultSet(index, count);
	}

	@Override
	public void free() {
		// held in memory
	}

	private int slice(long index, int count) throws SQLException {
		if (index < 1 || count < 0 || index - 1 > elements.length) {
			throw new SQLException("no elements from " + index + " (" + count + ") in an array of " + elements.length);
		}
		return (int) (index - 1);
	}

	@Override
	public String toString() {
		return NestedValues.json(java.util.Arrays.asList(elements));
	}
}
