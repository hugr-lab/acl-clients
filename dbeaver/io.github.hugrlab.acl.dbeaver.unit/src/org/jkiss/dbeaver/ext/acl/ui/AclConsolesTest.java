package org.jkiss.dbeaver.ext.acl.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** A view-as copy of a URL-configured connection: the URL's catalog / schema go (the driver refuses them there). */
public class AclConsolesTest {
	@Test
	public void theUrlLosesOnlyItsStart() {
		assertEquals("jdbc:acl://h:1", AclConsoles.withoutStart("jdbc:acl://h:1?catalog=sales&schema=raw"));
		assertEquals("jdbc:acl://h:1?flow=password&x=1",
		             AclConsoles.withoutStart("jdbc:acl://h:1?flow=password&catalog=sales&x=1"));
		assertEquals("jdbc:acl://h:1?flow=device", AclConsoles.withoutStart("jdbc:acl://h:1?flow=device"));
		assertEquals("jdbc:acl://h:1", AclConsoles.withoutStart("jdbc:acl://h:1"));
		assertEquals("jdbc:acl://h:1?a=1", AclConsoles.withoutStart("jdbc:acl://h:1?SCHEMA=x&a=1"));
	}
}
