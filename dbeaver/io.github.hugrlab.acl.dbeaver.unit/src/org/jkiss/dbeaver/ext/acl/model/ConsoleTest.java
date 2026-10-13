package org.jkiss.dbeaver.ext.acl.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.Test;

/** Spec 007.2: which console a principal is offered, and how each connection differs from the user's. */
public class ConsoleTest {
	private static ConsoleInfo info(Set<String> bundles, Set<String> catalogs, Set<String> views, Set<String> functions) {
		return new ConsoleInfo(bundles, catalogs, views, functions);
	}

	/** Spec 008.1: an editor is offered where its platform function is callable - in a catalog it administers. */
	@Test
	public void whatAnEditorIsOffered() {
		ConsoleInfo policy = info(Set.of("policy"), Set.of(), Set.of("roles"), Set.of("create_role", "add_relation"));
		assertTrue(policy.mayCall("create_role", null));
		assertTrue(policy.mayCall("add_relation", "sales"));
		assertFalse("not callable", policy.mayCall("drop_role", null));
		ConsoleInfo curator = info(Set.of(), Set.of("Scratch"), Set.of("catalogs"), Set.of("add_relation"));
		assertTrue("its catalog, folded as the node folds names", curator.mayCall("add_relation", "scratch"));
		assertFalse("another catalog", curator.mayCall("add_relation", "sales"));
		ConsoleInfo point = info(Set.of(), Set.of(), Set.of(), Set.of("grant_catalog"));
		assertTrue("a point grant: any catalog", point.mayCall("grant_catalog", "sales"));
	}

	/** Spec 125: the node says what it is and what it grants; an older node, which says neither, keeps today's. */
	@Test
	public void theNodeSaysWhatItIs() {
		ConsoleInfo open = new ConsoleInfo(Set.of("policy"), Set.of(), Set.of(), Set.of(), "standalone",
		                                   List.of("observe", "operate", "policy", "manage", "passthrough"));
		assertTrue(open.standalone());
		assertEquals("the node's own scopes, in its order", List.of("observe", "operate", "policy", "manage", "passthrough"),
		             open.grantableScopes());
		ConsoleInfo cluster = new ConsoleInfo(Set.of(), Set.of(), Set.of(), Set.of(), "cluster", List.of("cluster"));
		assertFalse(cluster.standalone());
		ConsoleInfo older = info(Set.of(), Set.of(), Set.of(), Set.of());
		assertFalse("an older node is not known to be standalone", older.standalone());
		assertEquals(List.of("observe", "operate", "cluster", "policy", "manage", "passthrough"), older.grantableScopes());
	}

	@Test
	public void whatIsOffered() {
		ConsoleInfo policy = info(Set.of("policy"), Set.of(), Set.of("roles", "catalogs"), Set.of("view_as", "console_info"));
		assertTrue(policy.offers(ConsoleKind.MANAGE));
		assertFalse(policy.offers(ConsoleKind.NATIVE));
		assertTrue(policy.offers(ConsoleKind.VIEW_AS));

		ConsoleInfo passthrough = info(Set.of("passthrough"), Set.of(), Set.of("roles"), Set.of("view_as"));
		assertTrue(passthrough.offers(ConsoleKind.NATIVE));

		// a catalog's manage: a management console, no view-as (view_as is the policy bundle's)
		ConsoleInfo catalog = info(Set.of(), Set.of("sales"), Set.of("catalogs"), Set.of("check_catalog"));
		assertTrue(catalog.offers(ConsoleKind.MANAGE));
		assertFalse(catalog.offers(ConsoleKind.VIEW_AS));
		assertFalse(catalog.offers(ConsoleKind.NATIVE));

		// view_as callable without reading platform.roles: nothing to choose from - not offered
		assertFalse(info(Set.of("x"), Set.of(), Set.of(), Set.of("view_as")).offers(ConsoleKind.VIEW_AS));

		ConsoleInfo none = info(Set.of(), Set.of(), Set.of(), Set.of("console_info"));
		assertFalse(none.offersAny());
		assertTrue(policy.offersAny());
	}

	@Test
	public void eachCopyChangesOneThing() {
		ConsoleCopy manage = ConsoleCopy.of(ConsoleKind.MANAGE, "node", null);
		assertEquals("node · manage", manage.name());
		assertEquals("manage", manage.mode());
		assertNull(manage.viewAs());
		assertFalse(manage.readOnly());
		assertNull(manage.connectionType());
		assertTrue("a console would prefix the user's bootstrap statements", manage.dropInitQueries());
		assertEquals("a management statement is not held for a commit", Boolean.TRUE, manage.autoCommit());

		ConsoleCopy nativeConsole = ConsoleCopy.of(ConsoleKind.NATIVE, "node", null);
		assertEquals("native", nativeConsole.mode());
		assertEquals("prod", nativeConsole.connectionType());
		assertEquals("DBeaver's prod type is manual-commit; a native statement commits", Boolean.TRUE,
		             nativeConsole.autoCommit());

		ConsoleCopy viewAs = ConsoleCopy.of(ConsoleKind.VIEW_AS, "node", "o'brien");
		assertEquals("node · as o'brien", viewAs.name());
		assertEquals("data", viewAs.mode());
		assertEquals("the driver sets it at connect, a refusal fails the connect", "o'brien", viewAs.viewAs());
		assertTrue(viewAs.readOnly());
		assertEquals("test", viewAs.connectionType());
		assertFalse(viewAs.dropInitQueries());

		assertNull(ConsoleCopy.unviewable("analyst"));
		assertNotNull("DBeaver would substitute it", ConsoleCopy.unviewable("${user}"));
	}

	@Test
	public void aListAsTheDriverAnswersIt() throws Exception {
		assertEquals(Set.of("a", "b"), AclDataSource.strings(List.of("a", "b")));
		// what the driver answers for a VARCHAR[]: a java.sql.Array
		java.sql.Array array = (java.sql.Array) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
		    new Class<?>[] {java.sql.Array.class},
		    (proxy, method, args) -> "getArray".equals(method.getName()) ? new Object[] {"policy", "observe"} : null);
		assertEquals(Set.of("policy", "observe"), AclDataSource.strings(array));
		assertEquals(Set.of("a"), AclDataSource.strings(new Object[] {"a", null}));
		assertTrue(AclDataSource.strings(null).isEmpty());
		assertEquals(ConsoleKind.VIEW_AS, ConsoleKind.of("view_as"));
	}
}
