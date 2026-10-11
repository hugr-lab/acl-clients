package org.jkiss.dbeaver.ext.acl.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
		assertTrue(manage.initQueries().isEmpty());
		assertFalse(manage.readOnly());
		assertNull(manage.connectionType());

		ConsoleCopy nativeConsole = ConsoleCopy.of(ConsoleKind.NATIVE, "node", null);
		assertEquals("native", nativeConsole.mode());
		assertEquals("prod", nativeConsole.connectionType());

		ConsoleCopy viewAs = ConsoleCopy.of(ConsoleKind.VIEW_AS, "node", "o'brien");
		assertEquals("node · as o'brien", viewAs.name());
		assertEquals("data", viewAs.mode());
		assertEquals(List.of("SET SESSION VIEW AS ROLE 'o''brien'"), viewAs.initQueries());
		assertTrue(viewAs.readOnly());
		assertEquals("test", viewAs.connectionType());
	}

	@Test
	public void aListAsTheDriverAnswersIt() throws Exception {
		assertEquals(Set.of("a", "b"), AclDataSource.strings(List.of("a", "b")));
		assertEquals(Set.of("a"), AclDataSource.strings(new Object[] {"a", null}));
		assertTrue(AclDataSource.strings(null).isEmpty());
		assertEquals(ConsoleKind.VIEW_AS, ConsoleKind.of("view_as"));
	}
}
