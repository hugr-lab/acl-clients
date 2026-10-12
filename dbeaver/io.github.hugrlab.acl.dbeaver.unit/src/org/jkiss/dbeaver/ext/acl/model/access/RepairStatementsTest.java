package org.jkiss.dbeaver.ext.acl.model.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/** Spec 008.2: a finding's repair split into the statements one may run, and their places to fill. */
public class RepairStatementsTest {
	@Test
	public void alternatives() {
		assertEquals(List.of("REPAIR VIRTUAL TABLE c.a REMAP (gone = <column>)", "REPAIR VIRTUAL TABLE c.a DROP MISSING COLUMNS"),
		             RepairStatements.alternatives("REPAIR VIRTUAL TABLE c.a REMAP (gone = <column>)  -- or DROP MISSING COLUMNS", "c.a"));
		assertEquals(List.of("ALTER VIRTUAL TABLE c.b SET PHYS <path>", "DROP VIRTUAL TABLE c.b"),
		             RepairStatements.alternatives("ALTER VIRTUAL TABLE c.b SET PHYS <path>  -- or DROP VIRTUAL TABLE c.b", "c.b"));
		assertEquals(List.of("ALTER VIRTUAL SCHEMA c.raw REFRESH"),
		             RepairStatements.alternatives("ALTER VIRTUAL SCHEMA c.raw REFRESH", "c.raw"));
		assertEquals(List.of(), RepairStatements.alternatives("", "c.x"));
	}

	@Test
	public void places() {
		assertTrue(RepairStatements.hasPlaces("ALTER VIRTUAL TABLE c.b SET PHYS <path>"));
		assertFalse(RepairStatements.hasPlaces("ALTER VIRTUAL TABLE c.b SET PHYS memory.main.t"));
		assertFalse("a comparison is no place", RepairStatements.hasPlaces("ALTER VIRTUAL TABLE c.b SET RLS (a < b)"));
	}
}
