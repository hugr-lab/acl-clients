package org.jkiss.dbeaver.ext.acl.model.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

/**
 * Spec 008.1: what each editor composes - the create statement with only the clauses filled, one statement per changed
 * property (none when nothing changed), the drop; names by spec 116 (case, spaces, `.` and `"` in a part, reserved
 * words). The same statements run against the dev node in the live tests.
 */
public class EditStatementsTest {
	private static Map<String, Object> row(Object... kv) {
		Map<String, Object> out = new HashMap<>();
		for (int i = 0; i + 1 < kv.length; i += 2) {
			out.put((String) kv[i], kv[i + 1]);
		}
		return out;
	}

	private static Map<String, Object> caps(String... held) {
		Map<String, Object> out = new java.util.LinkedHashMap<>();
		for (String cap : AclGrammar.CAPABILITIES) {
			out.put(cap, List.of(held).contains(cap));
		}
		return out;
	}

	@Test
	public void namesAndLiterals() {
		assertEquals("analyst", AclGrammar.ident("analyst"));
		assertEquals("\"Mixed Case\"", AclGrammar.ident("Mixed Case"));
		assertEquals("\"order\"", AclGrammar.ident("order"));
		assertEquals("\"say \"\"hi\"\"\"", AclGrammar.ident("say \"hi\""));
		assertEquals("raw.eu.events", AclGrammar.path("raw.eu.events"));
		assertEquals("\"a.b\"", AclGrammar.path("\"a.b\""));
		assertEquals("\"My Cat\".\"Order Items\"", AclGrammar.inCatalog("My Cat", "Order Items"));
		assertEquals("'it''s'", AclGrammar.literal("it's"));
		assertEquals(" WITH (select, insert)", AclGrammar.grantCaps(Set.of("insert", "select")));
		assertEquals(" CAPS '{}'", AclGrammar.grantCaps(Set.of()));
		assertEquals("", AclGrammar.grantCaps(null));
		assertEquals("'{\"select\": true, \"manage\": true}'", AclGrammar.capsJson(Set.of("manage", "select")));
		assertEquals("''", AclGrammar.capsJson(null));
	}

	@Test
	public void aRole() throws Exception {
		AclRole made = new AclRole(null, null, Map.of());
		made.markCreating();
		made.setName("Mixed Case");
		made.setDefaultClaims("tenant = 'a,b', unit = 'eu'");
		made.setComment("it's");
		assertEquals(List.of("CREATE ROLE \"Mixed Case\" CLAIMS (tenant = 'a,b', unit = 'eu')",
		                     "ALTER ROLE \"Mixed Case\" SET COMMENT 'it''s'"), made.createStatements());

		AclRole stored = new AclRole(null, null, row("role", "analyst", "comment", "old"));
		assertEquals(List.of(), stored.alterStatements(null, Set.of()));
		stored.setComment("new");
		stored.setDefaultClaims("");
		assertEquals(List.of("ALTER ROLE analyst SET CLAIMS ''", "ALTER ROLE analyst SET COMMENT 'new'"),
		             stored.alterStatements(null, Set.of("comment", "defaultClaims")));
		assertEquals(List.of("ALTER ROLE analyst SET COMMENT 'new'"), stored.alterStatements(null, Set.of("comment")));
		assertEquals(List.of("DROP ROLE analyst"), stored.dropStatements());
	}

	@Test
	public void aCatalog() throws Exception {
		AclVirtualCatalog made = new AclVirtualCatalog(null, null, Map.of());
		made.setName("My Cat");
		assertEquals(List.of("CREATE VIRTUAL CATALOG \"My Cat\""), made.createStatements());
		made.setComment("two");
		assertEquals(List.of("CREATE VIRTUAL CATALOG \"My Cat\" COMMENT 'two'"), made.createStatements());
		AclVirtualCatalog stored = new AclVirtualCatalog(null, null, row("catalog", "sales", "comment", "x"));
		assertEquals(List.of(), stored.alterStatements(null, Set.of()));
		stored.setComment("y");
		assertEquals(List.of("ALTER VIRTUAL CATALOG sales SET COMMENT 'y'"), stored.alterStatements(null, Set.of("comment")));
		assertEquals(List.of("DROP VIRTUAL CATALOG sales"), stored.dropStatements());
	}

	@Test
	public void aCatalogGrant() throws Exception {
		AclRole role = new AclRole(null, null, row("role", "Mixed Case"));
		AclCatalogGrant made = new AclCatalogGrant(null, role, Map.of());
		made.setName("sales");
		assertEquals(List.of("GRANT CATALOG sales TO ROLE \"Mixed Case\""), made.createStatements());
		made.setCapSelect(true);
		made.setCapInsert(true);
		made.setMain(true);
		made.setRls("amount > 0");
		made.setColumns("id, ssn = NULL");
		assertEquals(List.of("GRANT CATALOG sales TO ROLE \"Mixed Case\" WITH (select, insert) MAIN RLS (amount > 0) COLUMNS (id, ssn = NULL)"),
		             made.createStatements());
		made.setCapsUnstated(false);
		assertTrue(made.createStatements().get(0).contains(" CAPS '{}'"));

		AclCatalogGrant stored = new AclCatalogGrant(null, role, row("role", "analyst", "catalog", "sales", "is_main", true,
		                                                               "caps", caps("select"), "rls", "x > 1"));
		assertEquals(List.of(), stored.alterStatements(null, Set.of()));
		stored.setCapUpdate(true);
		stored.setMain(false);
		stored.setRls("");
		assertEquals(List.of("ALTER GRANT CATALOG sales TO ROLE analyst SET CAPS '{\"select\": true, \"update\": true}'",
		                     "ALTER GRANT CATALOG sales TO ROLE analyst SET MAIN false",
		                     "ALTER GRANT CATALOG sales TO ROLE analyst SET RLS ''"),
		             stored.alterStatements(null, Set.of("capUpdate", "main", "rls")));
		stored.setCapsUnstated(true);
		assertEquals(List.of("ALTER GRANT CATALOG sales TO ROLE analyst SET CAPS ''"),
		             stored.alterStatements(null, Set.of("capsUnstated")));
		assertEquals(List.of("REVOKE CATALOG sales FROM ROLE analyst"), stored.dropStatements());
	}

	@Test
	public void schemaAndObjectGrants() throws Exception {
		AclRole role = new AclRole(null, null, row("role", "analyst"));
		AclSchemaGrant schema = new AclSchemaGrant(null, role, row("role", "analyst", "catalog", "sales", "schema_path",
		                                                           "raw.eu", "caps", caps("select"), "inherited", true,
		                                                           "virtual_only", false, "comment", "eu"));
		assertEquals("an inherited row states no capabilities", "unstated: the catalog grant's", schema.getCaps());
		assertEquals(List.of(), schema.alterStatements(null, Set.of()));
		schema.setCapCreate(true);
		assertEquals(List.of("GRANT SCHEMA sales.raw.eu TO ROLE analyst WITH (create) COMMENT 'eu'"),
		             schema.alterStatements(null, Set.of("capCreate")));
		schema.setVirtualOnly(true);
		assertEquals(List.of("GRANT SCHEMA sales.raw.eu TO ROLE analyst WITH (create) VIRTUAL ONLY COMMENT 'eu'"),
		             schema.alterStatements(null, Set.of("virtualOnly")));
		schema.setInto("memory.staging");
		assertEquals("INTO wins over VIRTUAL ONLY (the grammar takes one)",
		             List.of("GRANT SCHEMA sales.raw.eu TO ROLE analyst WITH (create) INTO memory.staging COMMENT 'eu'"),
		             schema.alterStatements(null, Set.of("into")));
		assertEquals(List.of("REVOKE SCHEMA sales.raw.eu FROM ROLE analyst"), schema.dropStatements());

		AclObjectGrant object = new AclObjectGrant(null, role, Map.of());
		object.setCatalog("sales");
		object.setObject("\"a.b\"");
		object.setCapsUnstated(false);
		assertEquals(List.of("GRANT OBJECT sales.\"a.b\" TO ROLE analyst CAPS '{}'"), object.createStatements());
		AclObjectGrant stored = new AclObjectGrant(null, role, row("role", "analyst", "catalog", "sales", "object",
		                                                           "orders", "rls", "id > 0"));
		assertEquals("NULL caps: the catalog grant's", "unstated: the catalog grant's", stored.getCaps());
		stored.setColumns("id, amount");
		assertEquals(List.of("GRANT OBJECT sales.orders TO ROLE analyst RLS (id > 0) COLUMNS (id, amount)"),
		             stored.alterStatements(null, Set.of("columns")));
		assertEquals(List.of("REVOKE OBJECT sales.orders FROM ROLE analyst"), stored.dropStatements());
	}

	@Test
	public void aRelation() throws Exception {
		AclVirtualCatalog catalog = new AclVirtualCatalog(null, null, row("catalog", "My Cat"));
		AclRelation table = new AclRelation(null, catalog, Map.of());
		table.setName("Order Items");
		table.setPhys("memory.main.orders");
		assertEquals(List.of("CREATE VIRTUAL TABLE \"My Cat\".\"Order Items\" AS memory.main.orders"), table.createStatements());
		table.setDeclaredColumns("id NOT NULL, amount, ssn = NULL");
		table.setRls("amount > 0");
		table.setPrimaryKey("id");
		table.setComment("oi");
		table.setEnumTypes("varchar");
		assertEquals(List.of("CREATE VIRTUAL TABLE \"My Cat\".\"Order Items\" AS memory.main.orders COLUMNS (id NOT NULL, "
		                         + "amount, ssn = NULL) RLS (amount > 0) PRIMARY KEY (id) COMMENT 'oi'",
		                     "ALTER VIRTUAL TABLE \"My Cat\".\"Order Items\" SET TYPES (aliases = default, enums = varchar)"),
		             table.createStatements());

		AclRelation view = new AclRelation(null, catalog, Map.of());
		view.setName("v1");
		view.setKind("view");
		view.setViewSql("SELECT id FROM memory.main.orders");
		view.setPrimaryKey("id");
		assertEquals(List.of("CREATE VIRTUAL VIEW \"My Cat\".v1 PRIMARY KEY (id) AS SELECT id FROM memory.main.orders"),
		             view.createStatements());

		AclRelation stored = new AclRelation(null, catalog, row("catalog", "sales", "name", "raw.eu.events", "form", "alias",
		                                                        "phys", "memory.raw_eu.events"));
		assertEquals(List.of(), stored.alterStatements(null, Set.of()));
		stored.setPhys("memory.raw_eu.events2");
		stored.setDeclaredColumns("");
		stored.setRls("");
		stored.setPrimaryKey("");
		stored.setComment("c");
		stored.setAliasTypes("keep");
		assertEquals(List.of("ALTER VIRTUAL TABLE sales.raw.eu.events SET PHYS memory.raw_eu.events2",
		                     "ALTER VIRTUAL TABLE sales.raw.eu.events SET COLUMNS ''",
		                     "ALTER VIRTUAL TABLE sales.raw.eu.events SET RLS ''",
		                     "ALTER VIRTUAL TABLE sales.raw.eu.events DROP PRIMARY KEY",
		                     "ALTER VIRTUAL TABLE sales.raw.eu.events SET TYPES (aliases = keep, enums = default)",
		                     "COMMENT ON VIRTUAL TABLE sales.raw.eu.events IS 'c'"),
		             stored.alterStatements(null, Set.of("comment", "aliasTypes", "primaryKey", "rls", "declaredColumns", "phys")));
		AclRelation storedView = new AclRelation(null, catalog, row("catalog", "sales", "name", "big", "form", "view",
		                                                            "view_sql", "SELECT 1"));
		storedView.setViewSql("SELECT 2");
		assertEquals(List.of("ALTER VIRTUAL VIEW sales.big SET AS SELECT 2"), storedView.alterStatements(null, Set.of("viewSql")));
		assertEquals(List.of("DROP VIRTUAL VIEW sales.big"), storedView.dropStatements());
	}
}
