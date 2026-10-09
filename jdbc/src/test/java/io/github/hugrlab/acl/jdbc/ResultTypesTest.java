package io.github.hugrlab.acl.jdbc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Struct;
import java.sql.Types;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The duckdb types read off Arrow's objects by reflection, and what the result set makes of them. */
class ResultTypesTest {
	/** Arrow's Field: its metadata carries the node's TYPE_NAME. */
	public static final class FakeField {
		private final String type;

		FakeField(String type) {
			this.type = type;
		}

		public Map<String, String> getMetadata() {
			return type == null ? Map.of() : Map.of(ResultTypes.TYPE_NAME, type);
		}
	}

	/** Arrow's Schema. */
	public static final class FakeSchema {
		private final List<FakeField> fields;

		FakeSchema(String... types) {
			this.fields = Arrays.stream(types).map(FakeField::new).toList();
		}

		public List<FakeField> getFields() {
			return fields;
		}
	}

	/** ArrowFlightSqlClientHandler.PreparedStatement: the dataset schema of the prepare. */
	public static final class FakeHandle {
		public FakeSchema getDataSetSchema() {
			return new FakeSchema("STRUCT(a INTEGER)", "INTEGER");
		}
	}

	/** ArrowFlightPreparedStatement: the handle in a private field named preparedStatement (Arrow 19). */
	static final class FakePrepared {
		@SuppressWarnings("unused")
		private final FakeHandle preparedStatement = new FakeHandle();
	}

	// review 2026-10-09: a prepared statement's metadata never had duckdb's types
	@Test
	void aPreparedStatementsDatasetSchema() {
		assertArrayEquals(new String[] {"STRUCT(a INTEGER)", "INTEGER"}, ResultTypes.of(new FakePrepared(), 2));
		assertNull(ResultTypes.of(new FakePrepared(), 3), "a schema of another width is not this result's");
		assertNull(ResultTypes.of(new Object(), 1));
	}

	/** Arrow's result set: the flight's schema in a private field named schema; values from a script. */
	static final class FakeResult extends ForwardingResultSet {
		@SuppressWarnings("unused")
		private final FakeSchema schema;

		FakeResult(FakeSchema schema, Object... row) {
			super(delegate(row));
			this.schema = schema;
		}

		private static ResultSet delegate(Object[] row) {
			ResultSetMetaData meta = (ResultSetMetaData) Proxy.newProxyInstance(ResultTypesTest.class.getClassLoader(),
			    new Class<?>[] {ResultSetMetaData.class}, (p, m, a) -> switch (m.getName()) {
				    case "getColumnCount" -> row.length;
				    default -> throw new UnsupportedOperationException(m.getName());
			    });
			return (ResultSet) Proxy.newProxyInstance(ResultTypesTest.class.getClassLoader(),
			    new Class<?>[] {ResultSet.class}, (p, m, a) -> switch (m.getName()) {
				    case "getMetaData" -> meta;
				    case "getObject" -> row[(Integer) a[0] - 1];
				    default -> throw new UnsupportedOperationException(m.getName());
			    });
		}
	}

	private static Map<String, Object> arrowStruct(Object a) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("a", a);
		return m;
	}

	// review 2026-10-09: a STRUCT inside a LIST came out as Arrow's map, never an AclStruct
	@Test
	void aStructInAListIsAStruct() throws SQLException {
		List<Object> list = Arrays.asList(arrowStruct(1), null, arrowStruct(3));
		ResultSet rs = AclResultSet.wrap(new FakeResult(new FakeSchema("STRUCT(a INTEGER)[]", "STRUCT(a INTEGER)",
		    "MAP(VARCHAR, INTEGER)"), list, arrowStruct(7), Map.of("k", 1)), null, false);

		Array array = assertInstanceOf(Array.class, rs.getObject(1));
		assertEquals("STRUCT(a INTEGER)", array.getBaseTypeName());
		assertEquals(Types.STRUCT, array.getBaseType());
		Object[] elements = (Object[]) array.getArray();
		assertEquals(3, elements.length);
		Struct first = assertInstanceOf(Struct.class, elements[0]);
		assertArrayEquals(new Object[] {1}, first.getAttributes());
		assertNull(elements[1]);
		assertArrayEquals(new Object[] {3}, ((Struct) ((Object[]) array.getArray(3, 1))[0]).getAttributes());

		ResultSet items = array.getResultSet();
		assertTrue(items.next());
		assertEquals(1L, items.getLong("INDEX"));
		assertInstanceOf(Struct.class, items.getObject("VALUE"));
		assertTrue(items.next());
		assertTrue(items.next());
		assertEquals(3L, items.getLong(1));
		assertFalse(items.next());

		Array same = rs.getArray(1);
		assertInstanceOf(Struct.class, ((Object[]) same.getArray())[2]);
		assertInstanceOf(Struct.class, ((Object[]) rs.getObject(1, Array.class).getArray())[0]);

		// getObject(int, Class) for the nested kinds
		assertArrayEquals(new Object[] {7}, rs.getObject(2, Struct.class).getAttributes());
		assertEquals(Map.of("k", 1), rs.getObject(3, Map.class));
		assertEquals("{\"a\":7}", rs.getObject(2, String.class));
		assertThrows(SQLException.class, () -> rs.getObject(2, Array.class));
	}

	@Test
	void nestedJsonStaysText() throws SQLException {
		ResultSet rs = AclResultSet.wrap(new FakeResult(new FakeSchema("STRUCT(a INTEGER)[]"),
		    List.of(arrowStruct(1))), null, true);
		assertEquals("[{\"a\":1}]", rs.getObject(1));
		assertEquals("[{\"a\":1}]", rs.getObject(1, String.class));
	}
}
