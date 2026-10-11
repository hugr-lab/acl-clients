package org.jkiss.dbeaver.ext.acl.model.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/** Spec 007.3: what the Access control branch asks the node, and how it shows the answers. */
public class PlatformSqlTest {
	@Test
	public void oneViewOrFunctionNarrowedByLiterals() {
		assertEquals("SELECT * FROM platform.\"roles\"", PlatformSql.view("roles"));
		assertEquals("SELECT * FROM platform.\"grants\" WHERE \"role\" = 'o''brien'", PlatformSql.view("grants", "role", "o'brien"));
		assertEquals("SELECT * FROM platform.\"relation_columns\" WHERE \"catalog\" = 'sales' AND \"relation\" = 'raw.eu.t'",
		             PlatformSql.view("relation_columns", "catalog", "sales", "relation", "raw.eu.t"));
		assertEquals("SELECT * FROM platform.\"effective_rights\"('analyst', 'sales')",
		             PlatformSql.call("effective_rights", "analyst", "sales"));
	}

	@Test
	public void valuesAsThePanelShowsThem() {
		Map<String, Object> caps = new LinkedHashMap<>();
		caps.put("select", true);
		caps.put("insert", false);
		caps.put("manage", true);
		assertEquals("select, manage", PlatformValues.text(caps));
		assertEquals("a, b", PlatformValues.text(List.of("a", "b")));
		assertEquals("a", PlatformValues.text(new Object[] {"a", null}));
		Map<String, Object> struct = new LinkedHashMap<>();
		struct.put("path", "realm_access.roles");
		struct.put("op", "contains");
		assertEquals("path=realm_access.roles, op=contains", PlatformValues.text(struct));
		assertNull(PlatformValues.text(null));
		assertEquals("", PlatformValues.text(Map.of()));
		// what the driver answers for a STRUCT: a java.sql.Struct whose fields it names (getMap)
		assertEquals("select, manage", PlatformValues.text(new NamedStruct(caps)));
		// a grant's capabilities as the node means them (spec 012)
		assertEquals("unstated: every data capability", PlatformValues.caps(null, "unstated: every data capability"));
		Map<String, Object> nothing = new LinkedHashMap<>();
		nothing.put("select", false);
		assertEquals("none", PlatformValues.caps(new NamedStruct(nothing), "x"));
		assertEquals("select, manage", PlatformValues.caps(new NamedStruct(caps), "x"));
	}

	/** The driver's struct, as far as the plugin reads it: positional attributes and its fields by name. */
	public static final class NamedStruct implements java.sql.Struct {
		private final Map<String, Object> fields;

		NamedStruct(Map<String, Object> fields) {
			this.fields = fields;
		}

		public Map<String, Object> getMap() {
			return fields;
		}

		@Override
		public String getSQLTypeName() {
			return "STRUCT";
		}

		@Override
		public Object[] getAttributes() {
			return fields.values().toArray();
		}

		@Override
		public Object[] getAttributes(Map<String, Class<?>> map) {
			return getAttributes();
		}
	}
}
