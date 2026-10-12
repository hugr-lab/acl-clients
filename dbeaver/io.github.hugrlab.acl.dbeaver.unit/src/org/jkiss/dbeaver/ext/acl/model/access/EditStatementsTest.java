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

	@Test
	public void schemasFunctionsAndReferences() throws Exception {
		AclVirtualCatalog catalog = new AclVirtualCatalog(null, null, row("catalog", "c"));
		AclVirtualSchema alias = new AclVirtualSchema(null, catalog, Map.of());
		alias.setName("raw.eu");
		alias.setPhysPath("memory.raw_eu");
		alias.setComment("eu");
		assertEquals(List.of("CREATE VIRTUAL SCHEMA c.raw.eu AS memory.raw_eu COMMENT 'eu'"), alias.createStatements());
		alias.setKind("expansion");
		assertEquals(List.of("CREATE VIRTUAL SCHEMA c.raw.eu FROM memory.raw_eu COMMENT 'eu'"), alias.createStatements());
		AclVirtualSchema stored = new AclVirtualSchema(null, catalog, row("catalog", "c", "path", "raw", "phys_path", "memory.s2"));
		assertEquals("alias", stored.getKind());
		stored.setPhysPath("memory.s3");
		stored.setComment("y");
		assertEquals(List.of("ALTER VIRTUAL SCHEMA c.raw SET PHYS memory.s3", "COMMENT ON VIRTUAL SCHEMA c.raw IS 'y'"),
		             stored.alterStatements(null, Set.of("physPath", "comment")));
		assertEquals(List.of("DROP VIRTUAL SCHEMA c.raw"), stored.dropStatements());
		AclVirtualSchema expansion = new AclVirtualSchema(null, catalog, row("catalog", "c", "path", "cur", "origin", "memory.s2"));
		assertEquals("expansion", expansion.getKind());
		assertEquals("ALTER VIRTUAL SCHEMA c.cur REFRESH PRUNE", expansion.refreshStatement(true));

		AclFunctionEntry macro = new AclFunctionEntry(null, catalog, Map.of());
		macro.setName("perday");
		macro.setParams("m INTEGER");
		macro.setReturns("id INTEGER NOT NULL, amount INTEGER");
		macro.setPrimaryKey("id");
		macro.setTemplate("SELECT id, amount FROM memory.main.orders WHERE amount >= acl_arg(1)");
		assertEquals(List.of("CREATE VIRTUAL TABLE FUNCTION c.perday(m INTEGER) RETURNS TABLE (id INTEGER NOT NULL, amount "
		                     + "INTEGER) PRIMARY KEY (id) AS SELECT id, amount FROM memory.main.orders WHERE amount >= acl_arg(1)"),
		             macro.createStatements());
		AclFunctionEntry scalar = new AclFunctionEntry(null, catalog, Map.of());
		scalar.setName("shout");
		scalar.setKind("scalar");
		scalar.setParams("text VARCHAR");
		scalar.setReturns("VARCHAR");
		scalar.setTemplate("upper(acl_arg(1))");
		assertEquals(List.of("CREATE VIRTUAL SCALAR c.shout(text VARCHAR) RETURNS VARCHAR AS upper(acl_arg(1))"),
		             scalar.createStatements());
		AclFunctionEntry alias2 = new AclFunctionEntry(null, catalog, Map.of());
		alias2.setName("rng");
		alias2.setForm("alias");
		alias2.setTarget("range");
		assertEquals(List.of("CREATE VIRTUAL TABLE FUNCTION c.rng ALIAS OF range"), alias2.createStatements());
		AclFunctionEntry storedFn = new AclFunctionEntry(null, catalog, row("catalog", "c", "name", "sh", "kind", "scalar",
		                                                                   "form", "macro", "template", "upper(acl_arg(1))"));
		storedFn.setTemplate("lower(acl_arg(1))");
		storedFn.setComment("low");
		assertEquals(List.of("ALTER VIRTUAL SCALAR c.sh SET MACRO lower(acl_arg(1))", "COMMENT ON VIRTUAL SCALAR c.sh IS 'low'"),
		             storedFn.alterStatements(null, Set.of("template", "comment")));
		assertEquals(List.of("DROP VIRTUAL SCALAR c.sh"), storedFn.dropStatements());

		AclReferenceEntry reference = new AclReferenceEntry(null, catalog, Map.of());
		reference.setName("orders_customer");
		reference.setFromObject("orders");
		reference.setToObject("customers");
		reference.setPairs("customer_id = id");
		reference.setCardinality("many_to_one");
		reference.setComment("the customer");
		assertEquals(List.of("CREATE VIRTUAL REFERENCE c.orders_customer FROM orders TO customers ON (customer_id = id) "
		                     + "CARDINALITY many_to_one COMMENT 'the customer'"), reference.createStatements());
		AclReferenceEntry toFunction = new AclReferenceEntry(null, catalog, Map.of());
		toFunction.setName("cust_orders");
		toFunction.setFromObject("customers");
		toFunction.setToObject("orders_of");
		toFunction.setToKind("function");
		toFunction.setArguments("cust => id");
		toFunction.setOptional(true);
		assertEquals(List.of("CREATE VIRTUAL REFERENCE c.cust_orders FROM customers TO FUNCTION orders_of(cust => id) OPTIONAL"),
		             toFunction.createStatements());
		AclReferenceEntry storedRef = new AclReferenceEntry(null, catalog, row("catalog", "c", "name", "ab", "optional", false,
		                                                                       "cardinality", "many_to_one"));
		storedRef.setOptional(true);
		storedRef.setCardinality("one_to_one");
		assertEquals(List.of("ALTER VIRTUAL REFERENCE c.ab SET CARDINALITY one_to_one", "ALTER VIRTUAL REFERENCE c.ab SET OPTIONAL true"),
		             storedRef.alterStatements(null, Set.of("optional", "cardinality")));
		assertEquals(List.of("DROP VIRTUAL REFERENCE c.ab"), storedRef.dropStatements());
	}

	@Test
	public void identity() throws Exception {
		AclIssuer issuer = new AclIssuer(null, null, Map.of());
		issuer.setName("kc");
		issuer.setUrl("https://kc/realms/x");
		issuer.setSecret("kc_keys");
		issuer.setSecretService("corp");
		assertEquals(List.of("CREATE ISSUER kc URL 'https://kc/realms/x' FROM SECRET kc_keys IN corp"), issuer.createStatements());
		AclIssuer stored = new AclIssuer(null, null, row("name", "kc", "url", "https://a", "secret", "s"));
		stored.setUrl("https://b");
		stored.setSecret("");
		assertEquals(List.of("ALTER ISSUER kc SET URL 'https://b'", "ALTER ISSUER kc DROP FROM SECRET"),
		             stored.alterStatements(null, Set.of("url", "secret")));

		AclClient client = new AclClient(null, stored, Map.of());
		client.setName("desktop");
		client.setAudiences("'account'");
		client.setRequires("tier = 'gold', 'x' IN groups");
		client.setRolesFrom("'realm_access.roles'");
		client.setUnmapped("as_role");
		client.setClientId("acl-desktop");
		client.setFlows("authcode, device");
		assertEquals(List.of("CREATE CLIENT desktop ISSUER kc AUDIENCES ('account') REQUIRE (tier = 'gold', 'x' IN groups) "
		                     + "ROLES FROM ('realm_access.roles') UNMAPPED AS ROLE CLIENT ID 'acl-desktop' FLOWS (authcode, device)"),
		             client.createStatements());
		Map<String, Object> requires = new java.util.LinkedHashMap<>();
		requires.put("path", "region");
		requires.put("op", "in");
		requires.put("values", List.of("eu", "us"));
		Map<String, Object> constant = new java.util.LinkedHashMap<>();
		constant.put("name", "fixed");
		constant.put("paths", List.of());
		constant.put("constant", "v");
		AclClient storedClient = new AclClient(null, stored, row("name", "desktop", "issuer", "kc", "azp", List.of("a"),
		                                                         "requires", List.of(requires), "attributes", List.of(constant),
		                                                         "implicit", false));
		assertEquals("region IN ('eu', 'us')", storedClient.getRequires());
		assertEquals("fixed = CONSTANT 'v'", storedClient.getAttributes());
		storedClient.setAzp("");
		storedClient.setTokenType("");
		assertEquals(List.of("ALTER CLIENT desktop SET AZP ()", "ALTER CLIENT desktop SET TOKEN TYPE ''"),
		             storedClient.alterStatements(null, Set.of("azp", "tokenType")));
		AclClient implicit = new AclClient(null, stored, row("name", "kc", "issuer", "kc", "implicit", true));
		implicit.setRolesFrom("'roles'");
		assertEquals("the implicit client changes through its issuer", List.of("ALTER ISSUER kc SET ROLES FROM ('roles')"),
		             implicit.alterStatements(null, Set.of("rolesFrom")));

		AclRoleMapping mapping = new AclRoleMapping(null, null, Map.of());
		mapping.setExternalValue("steward");
		mapping.setScopeName("desktop");
		mapping.setRole("steward");
		assertEquals(List.of("MAP CLAIM 'steward' FROM CLIENT desktop TO ROLE steward"), mapping.createStatements());
		mapping.setSource("group");
		mapping.setScopeKind("issuer");
		assertEquals(List.of("DROP MAP GROUP 'steward' FROM ISSUER desktop TO ROLE steward"), mapping.dropStatements());
	}

	@Test
	public void functionsGroupsAndAdministration() throws Exception {
		AclFunctionCategory category = new AclFunctionCategory(null, null, Map.of());
		category.setName("mine");
		category.setComment("m");
		assertEquals(List.of("CREATE FUNCTION CATEGORY mine COMMENT 'm'"), category.createStatements());
		AclFunctionCategory storedCategory = new AclFunctionCategory(null, null, row("category", "mine"));
		AclCategoryMember member = new AclCategoryMember(null, storedCategory, Map.of());
		member.setFunction("range TABLE");
		assertEquals(List.of("ALTER FUNCTION CATEGORY mine ADD (range TABLE)"), member.createStatements());
		AclCategoryMember storedMember = new AclCategoryMember(null, storedCategory, row("database", "system", "schema",
		                                                                                 "main", "name", "upper", "kind", "scalar"));
		assertEquals(List.of("ALTER FUNCTION CATEGORY mine DROP (system.main.upper)"), storedMember.dropStatements());

		AclFunctionGrant grant = new AclFunctionGrant(null, null, Map.of());
		grant.setCategory("mine");
		grant.setRole("analyst");
		assertEquals(List.of("GRANT FUNCTION CATEGORY mine TO ROLE analyst"), grant.createStatements());
		grant.setAllowed(false);
		assertEquals(List.of("DENY FUNCTION CATEGORY mine TO ROLE analyst"), grant.createStatements());
		AclFunctionGrant everyone = new AclFunctionGrant(null, null, row("role", "", "database", "system", "schema", "main",
		                                                                 "name", "lower", "kind", "scalar", "allowed", true));
		assertEquals(List.of("REVOKE FUNCTION system.main.lower FROM ALL ROLES"), everyone.dropStatements());

		AclResourceGroup group = new AclResourceGroup(null, null, Map.of());
		group.setName("g");
		group.setWindowStart("8");
		group.setBatchBytes("32MiB");
		group.setDefault(true);
		assertEquals(List.of("CREATE RESOURCE GROUP g (window_start 8, batch_bytes '32MiB') DEFAULT"), group.createStatements());
		AclResourceGroup storedGroup = new AclResourceGroup(null, null, row("group", "g", "max_sessions", 5L, "is_default", true));
		storedGroup.setMaxSessions("7");
		storedGroup.setDefault(false);
		assertEquals(List.of("ALTER RESOURCE GROUP g SET (max_sessions 7)", "ALTER RESOURCE GROUP g DROP DEFAULT"),
		             storedGroup.alterStatements(null, Set.of("maxSessions", "default")));
		storedGroup.setMaxSessions("");
		try {
			storedGroup.alterStatements(null, Set.of("maxSessions"));
			org.junit.Assert.fail("a limit is never unset");
		} catch (org.jkiss.dbeaver.DBException expected) {
			assertTrue(expected.getMessage().contains("never unset"));
		}

		AclRole role = new AclRole(null, null, row("role", "auditor"));
		AclRoleGroup bound = new AclRoleGroup(null, role, Map.of());
		bound.setName("g");
		assertEquals(List.of("GRANT RESOURCE GROUP g TO ROLE auditor"), bound.createStatements());
		AclAdminScope scope = new AclAdminScope(null, role, Map.of());
		scope.setName("observe");
		assertEquals(List.of("GRANT ADMIN observe TO ROLE auditor"), scope.createStatements());
		assertEquals(List.of("REVOKE ADMIN observe FROM ROLE auditor"), scope.dropStatements());
		AclPlatformGrant view = new AclPlatformGrant(null, role, Map.of());
		view.setObject("sessions");
		assertEquals(List.of("GRANT VIEW platform.sessions TO ROLE auditor"), view.createStatements());
		view.setKind("function");
		view.setObject("kill_session");
		view.setAllowed(false);
		assertEquals(List.of("DENY FUNCTION platform.kill_session TO ROLE auditor"), view.createStatements());
		AclPlatformGrant source = new AclPlatformGrant(null, role, row("role", "auditor", "kind", "source", "object", "memory.main"));
		assertEquals(List.of("REVOKE SOURCE memory.main FROM ROLE auditor"), source.dropStatements());
	}
}
