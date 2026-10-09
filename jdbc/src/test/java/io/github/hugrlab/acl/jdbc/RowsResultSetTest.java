package io.github.hugrlab.acl.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hugrlab.acl.jdbc.RowsResultSet.ColumnSpec;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.Test;

class RowsResultSetTest {
	@Test
	void anEmptyAnswerKeepsItsColumns() throws SQLException {
		RowsResultSet rs = new RowsResultSet(AclDatabaseMetaData.PRIMARY_KEY_COLUMNS, List.of());
		assertFalse(rs.next());
		assertEquals(6, rs.getMetaData().getColumnCount());
		assertEquals("COLUMN_NAME", rs.getMetaData().getColumnName(4));
		assertEquals(Types.SMALLINT, rs.getMetaData().getColumnType(5));
		assertEquals(4, rs.findColumn("COLUMN_NAME"));
		assertThrows(SQLException.class, () -> rs.getString("COLUMN_NAME"), "no current row - but the column exists");
	}

	@Test
	void readsTheRows() throws SQLException {
		RowsResultSet rs = new RowsResultSet(List.of(ColumnSpec.text("NAME"), ColumnSpec.integer("N"),
		                                         ColumnSpec.bool("B")),
		    List.of(new Object[] {"a", 1, true}, new Object[] {null, null, null}));
		assertTrue(rs.next());
		assertEquals("a", rs.getString("name"));
		assertEquals(1, rs.getInt(2));
		assertEquals(1L, rs.getLong("N"));
		assertEquals("1", rs.getString(2));
		assertTrue(rs.getBoolean(3));
		assertTrue(rs.next());
		assertNull(rs.getString(1));
		assertTrue(rs.wasNull());
		assertEquals(0, rs.getInt(2));
		assertTrue(rs.wasNull());
		assertFalse(rs.next());
		assertTrue(rs.absolute(1));
		assertEquals("a", rs.getObject(1));
		rs.close();
		assertTrue(rs.isClosed());
		assertThrows(SQLException.class, rs::next);
	}

	@Test
	void everyMethodWithItsJdbcColumns() {
		assertEquals(24, AclDatabaseMetaData.COLUMN_COLUMNS.size());
		assertEquals(10, AclDatabaseMetaData.TABLE_COLUMNS.size());
		assertEquals(14, AclDatabaseMetaData.KEY_COLUMNS.size());
		assertEquals(17, AclDatabaseMetaData.FUNCTION_COLUMN_COLUMNS.size());
		assertEquals(20, AclDatabaseMetaData.PROCEDURE_COLUMN_COLUMNS.size());
		assertEquals(18, AclDatabaseMetaData.TYPE_INFO_COLUMNS.size());
	}
}
