package io.github.hugrlab.acl.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.Test;

class DuckTypesTest {
	@Test
	void nestedStruct() {
		DuckTypes.Type t = DuckTypes.parse("STRUCT(city VARCHAR, zip VARCHAR, geo STRUCT(lat DOUBLE, lon DOUBLE))");
		assertEquals(Types.STRUCT, t.jdbcType());
		assertEquals(List.of("city", "zip", "geo"), t.fields().stream().map(DuckTypes.Field::name).toList());
		DuckTypes.Type geo = t.fields().get(2).type();
		assertTrue(geo.isStruct());
		assertEquals("STRUCT(lat DOUBLE, lon DOUBLE)", geo.text());
		assertEquals(Types.DOUBLE, geo.fields().get(1).type().jdbcType());
	}

	@Test
	void quotedFieldNames() {
		DuckTypes.Type t = DuckTypes.parse("STRUCT(\"odd, name\" INTEGER, \"say \"\"hi\"\"\" VARCHAR)");
		assertEquals("odd, name", t.fields().get(0).name());
		assertEquals("say \"hi\"", t.fields().get(1).name());
		assertEquals(Types.VARCHAR, t.fields().get(1).type().jdbcType());
	}

	@Test
	void listsAndArraysAreArrays() {
		DuckTypes.Type list = DuckTypes.parse("INTEGER[]");
		assertEquals(Types.ARRAY, list.jdbcType()); // duckdb-java's text never matched LIST here
		assertEquals("LIST", list.base());
		assertEquals(Types.INTEGER, list.element().jdbcType());
		DuckTypes.Type fixed = DuckTypes.parse("INTEGER[3]");
		assertEquals(Types.ARRAY, fixed.jdbcType());
		assertEquals("ARRAY", fixed.base());
		DuckTypes.Type nested = DuckTypes.parse("STRUCT(a INTEGER)[][]");
		assertEquals(Types.ARRAY, nested.jdbcType());
		assertTrue(nested.element().element().isStruct());
		assertEquals(Types.ARRAY, DuckTypes.parse("VARCHAR[]").jdbcType());
	}

	@Test
	void mapAndUnionAreOther() {
		DuckTypes.Type map = DuckTypes.parse("MAP(VARCHAR, INTEGER)");
		assertEquals(Types.OTHER, map.jdbcType());
		assertTrue(map.isMap());
		assertEquals(Types.VARCHAR, map.key().jdbcType());
		assertEquals(Types.INTEGER, map.value().jdbcType());
		assertEquals(Types.OTHER, DuckTypes.parse("UNION(num INTEGER, str VARCHAR)").jdbcType());
		assertEquals(Types.ARRAY, DuckTypes.parse("MAP(VARCHAR, INTEGER)[]").jdbcType());
	}

	@Test
	void decimalCarriesSizeAndDigits() {
		DuckTypes.Type t = DuckTypes.parse("DECIMAL(18,3)");
		assertEquals(Types.DECIMAL, t.jdbcType());
		assertEquals(18, t.size());
		assertEquals(3, t.digits());
		assertEquals(38, DuckTypes.parse("NUMERIC(38, 10)").size());
	}

	@Test
	void enumIsVarchar() {
		assertEquals(Types.VARCHAR, DuckTypes.parse("ENUM('calm', 'busy')").jdbcType());
		assertEquals(Types.VARCHAR, DuckTypes.parse("ENUM('a,b', 'c)')").jdbcType());
	}

	@Test
	void timeZones() {
		assertEquals(Types.TIMESTAMP_WITH_TIMEZONE, DuckTypes.parse("TIMESTAMP WITH TIME ZONE").jdbcType());
		assertEquals(Types.TIMESTAMP_WITH_TIMEZONE, DuckTypes.parse("TIMESTAMPTZ").jdbcType());
		assertEquals(Types.TIME_WITH_TIMEZONE, DuckTypes.parse("TIME WITH TIME ZONE").jdbcType());
		assertEquals(Types.TIMESTAMP, DuckTypes.parse("TIMESTAMP").jdbcType());
	}

	@Test
	void scalars() {
		assertEquals(Types.INTEGER, DuckTypes.parse("INTEGER").jdbcType());
		assertEquals(10, DuckTypes.parse("INTEGER").size());
		assertEquals(Types.BIGINT, DuckTypes.parse("bigint").jdbcType());
		assertEquals(Types.BOOLEAN, DuckTypes.parse("BOOLEAN").jdbcType());
		assertEquals(Types.VARCHAR, DuckTypes.parse("VARCHAR").jdbcType());
		assertNull(DuckTypes.parse("VARCHAR").size());
		assertEquals(Types.BLOB, DuckTypes.parse("BLOB").jdbcType());
		assertEquals(Types.OTHER, DuckTypes.parse("UUID").jdbcType());
		assertEquals(Types.OTHER, DuckTypes.parse("SOMETHING_NEW").jdbcType());
		assertTrue(DuckTypes.parse("UBIGINT").isUnsigned());
	}

	// review 2026-10-09: anything starting with U was unsigned - UNKNOWN (no type text) among them
	@Test
	void unsignedAreTheFive() {
		for (String t : List.of("UTINYINT", "USMALLINT", "UINTEGER", "UBIGINT", "UHUGEINT")) {
			assertTrue(DuckTypes.parse(t).isUnsigned(), t);
		}
		for (String t : List.of("UUID", "UNION(a INTEGER)", "USER_TYPE", "INTEGER")) {
			assertFalse(DuckTypes.parse(t).isUnsigned(), t);
		}
		assertFalse(DuckTypes.parse(null).isUnsigned(), "UNKNOWN");
		assertFalse(DuckTypes.parse("").isUnsigned());
	}

	// review 2026-10-09: BIGNUM / BIT reach Arrow as duckdb's bytes - read as their text
	@Test
	void bignumAndBitFromTheirBytes() {
		DuckTypes.Type bignum = DuckTypes.parse("BIGNUM");
		assertEquals("123", DuckTypes.decodeBits(bignum, new byte[] {(byte) 0x80, 0, 1, 0x7B}));
		assertEquals("-123", DuckTypes.decodeBits(bignum, new byte[] {0x7F, (byte) 0xFF, (byte) 0xFE, (byte) 0x84}));
		assertEquals("256", DuckTypes.decodeBits(bignum, new byte[] {(byte) 0x80, 0, 2, 1, 0}));
		assertEquals(String.class.getName(), DuckTypes.javaClass(bignum));
		// '101': 5 padding bits, then 101
		assertEquals("101", DuckTypes.decodeBits(DuckTypes.parse("BIT"), new byte[] {5, (byte) 0xFD}));
		assertEquals("0000000011", DuckTypes.decodeBits(DuckTypes.parse("BIT"), new byte[] {6, (byte) 0xFC, 3}));
	}

	// a duckdb BIT is a bit string, not JDBC's one-bit boolean
	@Test
	void bitIsOther() {
		assertEquals(Types.OTHER, DuckTypes.parse("BIT").jdbcType());
		assertEquals(Types.OTHER, DuckTypes.parse("BITSTRING").jdbcType());
	}
}
