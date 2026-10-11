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
	}
}
