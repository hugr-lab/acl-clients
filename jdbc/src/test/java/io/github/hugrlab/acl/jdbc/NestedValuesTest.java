package io.github.hugrlab.acl.jdbc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.sql.SQLException;
import java.sql.Struct;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NestedValuesTest {
	/** Stands in for Arrow's shaded {@code Text}: matched by simple name, read by toString. */
	static final class Text {
		private final String s;

		Text(String s) {
			this.s = s;
		}

		@Override
		public String toString() {
			return s;
		}
	}

	private static final String ADDRESS = "STRUCT(city VARCHAR, zip VARCHAR, geo STRUCT(lat DOUBLE, lon DOUBLE))";

	private static Map<String, Object> arrowAddress() {
		Map<String, Object> geo = new LinkedHashMap<>();
		geo.put("lat", 52.5);
		geo.put("lon", 13.4);
		Map<String, Object> address = new LinkedHashMap<>();
		address.put("city", new Text("Berlin"));
		address.put("zip", null);
		address.put("geo", geo);
		return address;
	}

	@Test
	void structBecomesAclStructAtEveryDepth() throws SQLException {
		Object value = NestedValues.normalize(arrowAddress(), DuckTypes.parse(ADDRESS));
		Struct struct = assertInstanceOf(Struct.class, value);
		assertEquals(ADDRESS, struct.getSQLTypeName());
		Object[] attributes = struct.getAttributes();
		assertEquals("Berlin", attributes[0]);
		assertEquals(null, attributes[1]);
		Struct geo = assertInstanceOf(Struct.class, attributes[2]);
		assertEquals("STRUCT(lat DOUBLE, lon DOUBLE)", geo.getSQLTypeName());
		assertArrayEquals(new Object[] {52.5, 13.4}, geo.getAttributes());
		assertEquals(List.of("city", "zip", "geo"), List.copyOf(((AclStruct) struct).getMap().keySet()));
	}

	@Test
	void json() throws SQLException {
		Object value = NestedValues.normalize(arrowAddress(), DuckTypes.parse(ADDRESS));
		assertEquals("{\"city\":\"Berlin\",\"zip\":null,\"geo\":{\"lat\":52.5,\"lon\":13.4}}", NestedValues.json(value));
		Object list = NestedValues.normalize(Arrays.asList(new Text("a"), null, new Text("q\"t")),
		    DuckTypes.parse("VARCHAR[]"));
		assertEquals("[\"a\",null,\"q\\\"t\"]", NestedValues.json(list));
		Map<Object, Object> map = new LinkedHashMap<>();
		map.put(new Text("x"), 1);
		assertEquals("{\"x\":1}", NestedValues.json(NestedValues.normalize(map, DuckTypes.parse("MAP(VARCHAR, INTEGER)"))));
	}

	@Test
	void aclStructIsAStruct() throws SQLException {
		AclStruct s = new AclStruct("STRUCT(a INTEGER, b VARCHAR)", List.of("a", "b"), new Object[] {1, "x"});
		assertEquals("STRUCT(a INTEGER, b VARCHAR)", s.getSQLTypeName());
		assertArrayEquals(new Object[] {1, "x"}, s.getAttributes(Map.of()));
		assertEquals("{\"a\":1,\"b\":\"x\"}", s.toString());
		Object[] copy = s.getAttributes();
		copy[0] = 2;
		assertEquals(1, s.getAttributes()[0], "the attributes are the struct's own");
	}
}
