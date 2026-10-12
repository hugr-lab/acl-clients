package io.github.hugrlab.acl.dbeaver.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.ext.acl.model.access.AclCatalogGrant;
import org.jkiss.dbeaver.ext.acl.model.access.AclObjectGrant;
import org.jkiss.dbeaver.ext.acl.model.access.AclPlatformObject;
import org.jkiss.dbeaver.ext.acl.model.access.AclRelation;
import org.jkiss.dbeaver.ext.acl.model.access.AclRole;
import org.jkiss.dbeaver.ext.acl.model.access.AclVirtualCatalog;
import org.jkiss.dbeaver.ext.acl.model.access.PlatformSql;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.edit.DBECommand;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEObjectMaker;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCExecutionPurpose;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCStatement;
import org.jkiss.dbeaver.model.impl.edit.AbstractCommandContext;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.runtime.properties.PropertySourceEditable;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The editors (spec 008.1) in a running DBeaver against dev/node.sh, through DBeaver's own command path - the object
 * managers, a property edit as the properties panel makes it (PropertySourceEditable), the script a save previews,
 * saveChanges - as steward1 (the policy bundle), curator1 (manage on the catalog scratch only) and analyst1 (none).
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class LiveEditTest {
	private static final VoidProgressMonitor MONITOR = new VoidProgressMonitor();
	private static final String ROLE = "t008 Role";
	private static final String CATALOG = "t008cat";
	private static final String GRANTEE = "t008 Grantee";
	private static DBPDataSourceContainer steward;
	private static DBPDataSourceContainer curator;

	static synchronized AclDataSource steward() throws Exception {
		if (steward == null) {
			Workbench.waitUp();
			steward = LiveTreeTest.connect("steward1", "steward1-pass");
		}
		return (AclDataSource) steward.getDataSource();
	}

	static synchronized AclDataSource curator() throws Exception {
		if (curator == null) {
			Workbench.waitUp();
			curator = LiveTreeTest.connect("curator1", "curator1-pass");
		}
		return (AclDataSource) curator.getDataSource();
	}

	@AfterClass
	public static void cleanUp() throws Exception {
		if (steward != null) {
			run(steward(), "DROP VIRTUAL CATALOG IF EXISTS " + CATALOG + " CASCADE");
			run(steward(), "DROP ROLE IF EXISTS \"" + ROLE + "\"");
			run(steward(), "DROP ROLE IF EXISTS \"" + GRANTEE + "\"");
			run(steward(), "DROP ROLE IF EXISTS t008_txn");
		}
	}

	private static DBECommandContext context(AclDataSource acl) {
		DBCExecutionContext execution = DBUtils.getDefaultContext(acl, true);
		return new AbstractCommandContext(execution, false) {
		};
	}

	@SuppressWarnings("unchecked")
	private static <T extends AclPlatformObject> T create(DBECommandContext context, Class<T> type, DBSObject container)
	    throws DBException {
		DBEObjectMaker<T, DBSObject> maker = DBWorkbench.getPlatform().getEditorsRegistry().getObjectManager(type,
		                                                                                                 DBEObjectMaker.class);
		assertNotNull("a manager for " + type.getSimpleName(), maker);
		assertTrue("Create is offered", maker.canCreateObject(container));
		return maker.createNewObject(MONITOR, context, container, null, new HashMap<>());
	}

	@SuppressWarnings("unchecked")
	private static <T extends AclPlatformObject> void delete(DBECommandContext context, T object) throws DBException {
		DBEObjectMaker<T, DBSObject> maker = DBWorkbench.getPlatform().getEditorsRegistry()
		    .getObjectManager(object.getClass(), DBEObjectMaker.class);
		assertTrue("Delete is offered", maker.canDeleteObject(object));
		maker.deleteObject(context, object, new HashMap<>());
	}

	/** A property edit as the properties panel makes it: through the object's editor, into the command context. */
	private static void set(DBECommandContext context, Object object, String id, Object value) {
		PropertySourceEditable source = new PropertySourceEditable(context, object, object);
		source.collectProperties();
		assertTrue("property " + id + " is editable", source.isEditable(object));
		// by its descriptor: a grant's RLS and columns are hidden from the panel (their tabs edit them)
		source.setPropertyValue(MONITOR, object, org.jkiss.dbeaver.ext.acl.ui.grant.GrantPage.descriptor(source, object, id), value);
	}

	/** What Save previews: every command's statements, in order. */
	private static List<String> script(DBECommandContext context) throws DBException {
		List<String> out = new ArrayList<>();
		for (DBECommand<?> command : context.getFinalCommands()) {
			command.validateCommand(MONITOR, new HashMap<>());
			DBEPersistAction[] actions = command.getPersistActions(MONITOR, context.getExecutionContext(), new HashMap<>());
			if (actions != null) {
				for (DBEPersistAction action : actions) {
					out.add(action.getScript());
				}
			}
		}
		return out;
	}

	private static List<Map<String, Object>> rows(AclDataSource acl, String sql) throws DBException {
		return acl.platformRows(MONITOR, sql);
	}

	private static void run(AclDataSource acl, String sql) throws Exception {
		try (JDBCSession session = DBUtils.openUtilSession(MONITOR, acl, "t008");
		     JDBCStatement statement = session.createStatement()) {
			statement.execute(sql);
		}
	}

	private static <T extends AclPlatformObject> T find(List<T> list, String name) {
		for (T each : list) {
			if (name.equals(each.getName())) {
				return each;
			}
		}
		return null;
	}

	@Test
	public void aRoleFromCreateToDrop() throws Exception {
		LiveTreeTest.assumeNode();
		AclDataSource acl = steward();
		DBECommandContext context = context(acl);
		AclRole role = create(context, AclRole.class, acl);
		set(context, role, "name", ROLE);
		set(context, role, "comment", "made by a test");
		set(context, role, "defaultClaims", "tenant = 'acme'");
		assertEquals(List.of("CREATE ROLE \"t008 Role\" CLAIMS (tenant = 'acme')",
		                     "ALTER ROLE \"t008 Role\" SET COMMENT 'made by a test'"), script(context));
		context.saveChanges(MONITOR, new HashMap<>());
		assertEquals("made by a test", rows(acl, PlatformSql.view("roles", "role", ROLE)).get(0).get("comment"));
		assertEquals("acme", rows(acl, PlatformSql.view("role_claims", "role", ROLE)).get(0).get("value"));

		// read again: what the node holds; an edit of one property is one statement
		role.setPersisted(true);
		role.refreshObject(MONITOR);
		assertEquals("tenant = 'acme'", role.getDefaultClaims(MONITOR));
		DBECommandContext edit = context(acl);
		set(edit, role, "comment", "changed");
		assertEquals(List.of("ALTER ROLE \"t008 Role\" SET COMMENT 'changed'"), script(edit));
		edit.saveChanges(MONITOR, new HashMap<>());
		assertEquals("changed", rows(acl, PlatformSql.view("roles", "role", ROLE)).get(0).get("comment"));

		// changed on the node behind the form: asked first - "No" saves nothing
		role.refreshObject(MONITOR);
		run(acl, "ALTER ROLE \"t008 Role\" SET COMMENT 'meanwhile'");
		DBECommandContext stale = context(acl);
		set(stale, role, "comment", "mine");
		AtomicReference<Throwable> saved = new AtomicReference<>();
		Thread save = new Thread(() -> {
			try {
				stale.saveChanges(MONITOR, new HashMap<>());
			} catch (Throwable t) {
				saved.set(t);
			}
		});
		save.start();
		SWTBotShell asked = new SWTWorkbenchBot().shell("Changed on the node");
		String text = LiveConsoleTest.textsOf(asked);
		assertTrue(text, text.contains("comment: was 'changed', now 'meanwhile'"));
		asked.bot().button("No").click();
		save.join(30_000);
		assertNotNull("not saved", saved.get());
		assertTrue(saved.get().getMessage(), saved.get().getMessage().contains("was changed on the node meanwhile"));
		assertEquals("meanwhile", rows(acl, PlatformSql.view("roles", "role", ROLE)).get(0).get("comment"));

		DBECommandContext drop = context(acl);
		delete(drop, role);
		assertEquals(List.of("DROP ROLE \"t008 Role\""), script(drop));
		drop.saveChanges(MONITOR, new HashMap<>());
		assertTrue(rows(acl, PlatformSql.view("roles", "role", ROLE)).isEmpty());
	}

	@Test
	public void aCatalogItsTableAndTheGrants() throws Exception {
		LiveTreeTest.assumeNode();
		AclDataSource acl = steward();
		run(acl, "CREATE ROLE IF NOT EXISTS \"" + GRANTEE + "\"");
		DBECommandContext context = context(acl);
		AclVirtualCatalog catalog = create(context, AclVirtualCatalog.class, acl);
		set(context, catalog, "name", CATALOG);
		set(context, catalog, "comment", "a test's");
		context.saveChanges(MONITOR, new HashMap<>());
		catalog.setPersisted(true);
		assertEquals("a test's", rows(acl, PlatformSql.view("catalogs", "catalog", CATALOG)).get(0).get("comment"));

		DBECommandContext table = context(acl);
		AclRelation orders = create(table, AclRelation.class, catalog);
		set(table, orders, "name", "Order Items");
		set(table, orders, "phys", "memory.main.orders");
		set(table, orders, "declaredColumns", "id NOT NULL, amount, tenant");
		set(table, orders, "rls", "amount > 0");
		set(table, orders, "primaryKey", "id");
		assertEquals(List.of("CREATE VIRTUAL TABLE t008cat.\"Order Items\" AS memory.main.orders COLUMNS (id NOT NULL, "
		                     + "amount, tenant) RLS (amount > 0) PRIMARY KEY (id)"), script(table));
		table.saveChanges(MONITOR, new HashMap<>());
		orders.setPersisted(true);
		orders.refreshObject(MONITOR);
		assertEquals("id NOT NULL, amount, tenant", orders.getDeclaredColumns(MONITOR));
		assertEquals("id", orders.getPrimaryKey(MONITOR));
		assertEquals("amount > 0", orders.getRls());

		DBECommandContext narrow = context(acl);
		set(narrow, orders, "declaredColumns", "id NOT NULL, amount");
		set(narrow, orders, "comment", "two columns");
		assertEquals(List.of("ALTER VIRTUAL TABLE t008cat.\"Order Items\" SET COLUMNS (id NOT NULL, amount)",
		                     "COMMENT ON VIRTUAL TABLE t008cat.\"Order Items\" IS 'two columns'"), script(narrow));
		narrow.saveChanges(MONITOR, new HashMap<>());
		orders.refreshObject(MONITOR);
		assertEquals("id NOT NULL, amount", orders.getDeclaredColumns(MONITOR));
		assertEquals("two columns", orders.getComment());

		// the grants, under the role
		AclRole role = find(acl.getAccessRoles(MONITOR), GRANTEE);
		if (role == null) {
			acl.refreshObject(MONITOR);
			role = find(acl.getAccessRoles(MONITOR), GRANTEE);
		}
		assertNotNull(role);
		DBECommandContext grant = context(acl);
		AclCatalogGrant onCatalog = create(grant, AclCatalogGrant.class, role);
		set(grant, onCatalog, "name", CATALOG);
		set(grant, onCatalog, "capSelect", true);
		set(grant, onCatalog, "main", true);
		assertEquals(List.of("GRANT CATALOG t008cat TO ROLE \"t008 Grantee\" WITH (select) MAIN"), script(grant));
		grant.saveChanges(MONITOR, new HashMap<>());
		onCatalog.setPersisted(true);
		onCatalog.refreshObject(MONITOR);
		assertTrue(onCatalog.isCapSelect());
		assertFalse(onCatalog.isCapInsert());

		DBECommandContext widen = context(acl);
		set(widen, onCatalog, "capInsert", true);
		set(widen, onCatalog, "main", false);
		assertEquals(List.of("ALTER GRANT CATALOG t008cat TO ROLE \"t008 Grantee\" SET CAPS '{\"select\": true, \"insert\": true}'",
		                     "ALTER GRANT CATALOG t008cat TO ROLE \"t008 Grantee\" SET MAIN false"), script(widen));
		widen.saveChanges(MONITOR, new HashMap<>());
		onCatalog.refreshObject(MONITOR);
		assertTrue(onCatalog.isCapInsert());
		assertFalse(onCatalog.isMain());

		DBECommandContext object = context(acl);
		AclObjectGrant onTable = create(object, AclObjectGrant.class, role);
		set(object, onTable, "catalog", CATALOG);
		set(object, onTable, "object", "Order Items");
		set(object, onTable, "capSelect", true);
		set(object, onTable, "rls", "tenant = 'acme'");
		object.saveChanges(MONITOR, new HashMap<>());
		onTable.setPersisted(true);
		onTable.refreshObject(MONITOR);
		assertEquals("tenant = 'acme'", onTable.getRls());

		DBECommandContext revoke = context(acl);
		delete(revoke, onTable);
		assertEquals(List.of("REVOKE OBJECT t008cat.\"Order Items\" FROM ROLE \"t008 Grantee\""), script(revoke));
		revoke.saveChanges(MONITOR, new HashMap<>());
		assertTrue(rows(acl, PlatformSql.view("object_grants", "role", GRANTEE)).isEmpty());

		// the catalog is held by a grant: dropping it is refused by the node, which names the role
		DBECommandContext dropCatalog = context(acl);
		delete(dropCatalog, catalog);
		try {
			dropCatalog.saveChanges(MONITOR, new HashMap<>());
			fail("a catalog a role holds a grant on is dropped only with CASCADE");
		} catch (DBException e) {
			assertTrue(e.getMessage(), e.getMessage().contains(GRANTEE));
		}
	}

	@Test
	public void aCatalogAdministratorEditsItsOwnCatalogOnly() throws Exception {
		LiveTreeTest.assumeNode();
		AclDataSource acl = curator();
		AclVirtualCatalog scratch = find(acl.getAccessCatalogs(MONITOR), "scratch");
		assertNotNull("the curator's catalog is listed", scratch);
		DBECommandContext own = context(acl);
		AclRelation table = create(own, AclRelation.class, scratch);
		set(own, table, "name", "t008_mine");
		set(own, table, "phys", "memory.main.orders");
		own.saveChanges(MONITOR, new HashMap<>());
		assertEquals(1, rows(steward(), PlatformSql.view("relations", "catalog", "scratch", "name", "t008_mine")).size());
		run(steward(), "DROP VIRTUAL TABLE scratch.t008_mine");

		// another catalog: not offered (console_info: it administers scratch only) - and the node refuses the statement
		AclVirtualCatalog sales = new AclVirtualCatalog(acl, acl, Map.of("catalog", "sales"));
		DBEObjectMaker<AclRelation, DBSObject> maker = DBWorkbench.getPlatform().getEditorsRegistry()
		    .getObjectManager(AclRelation.class, DBEObjectMaker.class);
		assertFalse("Create is not offered in a catalog it does not administer", maker.canCreateObject(sales));
		assertFalse("nor is Create New Role", DBWorkbench.getPlatform().getEditorsRegistry()
		    .getObjectManager(AclRole.class, DBEObjectMaker.class).canCreateObject(acl));
		try {
			run(acl, "CREATE VIRTUAL TABLE sales.t008_theirs AS memory.main.orders");
			fail("a catalog admin writes only its catalogs");
		} catch (Exception e) {
			assertTrue(rows(steward(), PlatformSql.view("relations", "catalog", "sales", "name", "t008_theirs")).isEmpty());
		}
	}

	/** A save whose second statement is refused says the first ran (a management batch is not atomic). */
	@Test
	public void aRefusalSaysWhatRanBeforeIt() throws Exception {
		LiveTreeTest.assumeNode();
		AclDataSource acl = steward();
		AclVirtualCatalog scratch = find(acl.getAccessCatalogs(MONITOR), "scratch");
		assertNotNull(scratch);
		DBECommandContext context = context(acl);
		AclRelation table = create(context, AclRelation.class, scratch);
		set(context, table, "name", "t008_half");
		set(context, table, "phys", "memory.main.orders");
		table.setAliasTypes("bogus"); // the list offers base / keep: a value the node refuses, set past it
		try {
			context.saveChanges(MONITOR, new HashMap<>());
			fail("SET TYPES (aliases = bogus) is refused");
		} catch (DBException e) {
			String said = e.getMessage();
			assertTrue(said, said.contains("Applied before it"));
			assertTrue(said, said.contains("CREATE VIRTUAL TABLE scratch.t008_half AS memory.main.orders"));
		} finally {
			run(acl, "DROP VIRTUAL TABLE IF EXISTS scratch.t008_half");
		}
	}

	/** Spec 008.1 addendum: a grant checked with the node (platform.check_grant) before it is written. */
	@Test
	public void aGrantIsCheckedBeforeItIsWritten() throws Exception {
		LiveTreeTest.assumeNode();
		AclDataSource acl = steward();
		AclRole analyst = find(acl.getAccessRoles(MONITOR), "analyst");
		assertNotNull(analyst);
		AclObjectGrant orders = find(analyst.getObjectGrants(MONITOR), "sales.orders");
		assertNotNull("dev/node.sh grants analyst sales.orders", orders);
		List<String> columns = new ArrayList<>();
		for (Map<String, Object> row : orders.check(MONITOR, orders.getRls(), "")) {
			if ("column".equals(String.valueOf(row.get("item")))) {
				columns.add(String.valueOf(row.get("name")));
			}
		}
		assertEquals("no list: every column the object exposes", List.of("id", "tenant", "amount"), columns);
		String status = null;
		String message = null;
		for (Map<String, Object> row : orders.check(MONITOR, "nope > 1", "")) {
			if ("rls".equals(String.valueOf(row.get("item")))) {
				status = String.valueOf(row.get("status"));
				message = String.valueOf(row.get("message"));
			}
		}
		assertEquals("refused", status);
		assertTrue(message, message.contains("does not bind against it"));
		String mask = null;
		for (Map<String, Object> row : orders.check(MONITOR, "", "id, amount = 0")) {
			if ("column".equals(String.valueOf(row.get("item"))) && "amount".equals(String.valueOf(row.get("name")))) {
				mask = String.valueOf(row.get("message"));
			}
		}
		assertEquals("a mask is answered with its column", "0", mask);
		assertEquals("nothing written", "tenant = acl_claim('tenant')",
		             rows(acl, PlatformSql.view("object_grants", "role", "analyst", "object", "orders")).get(0).get("rls"));
	}

	@Test
	public void anOrdinaryRoleIsOfferedNoEditor() throws Exception {
		LiveTreeTest.assumeNode();
		AclDataSource acl = (AclDataSource) LiveTreeTest.analyst().getDataSource();
		DBEObjectMaker<?, ?> maker = DBWorkbench.getPlatform().getEditorsRegistry().getObjectManager(AclRole.class,
		                                                                                         DBEObjectMaker.class);
		assertFalse("no console_info, no editor", maker.canCreateObject(acl));
	}

	@Test
	public void aManagementWriteIsNotPartOfTheClientsTransaction() throws Exception {
		LiveTreeTest.assumeNode();
		AclDataSource acl = steward();
		DBCExecutionContext execution = DBUtils.getDefaultContext(acl, true);
		try (JDBCSession session = (JDBCSession) execution.openSession(MONITOR, DBCExecutionPurpose.UTIL, "t008 txn")) {
			java.sql.Connection connection = session.getOriginal();
			boolean auto = connection.getAutoCommit();
			try {
				connection.setAutoCommit(false);
				try (java.sql.Statement statement = connection.createStatement()) {
					statement.execute("CREATE ROLE t008_txn");
				}
				connection.rollback();
			} finally {
				connection.setAutoCommit(auto);
			}
		}
		assertEquals("applied whatever the client's transaction did (spec 008 section 3)", 1,
		             rows(acl, PlatformSql.view("roles", "role", "t008_txn")).size());
	}
}
