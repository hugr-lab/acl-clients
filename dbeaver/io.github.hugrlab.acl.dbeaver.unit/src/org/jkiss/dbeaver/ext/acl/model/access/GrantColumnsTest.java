package org.jkiss.dbeaver.ext.acl.model.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.jkiss.dbeaver.ext.acl.model.access.GrantColumns.Node;
import org.jkiss.dbeaver.ext.acl.model.access.GrantColumns.Shown;
import org.junit.Test;

/**
 * Spec 011 (duckdb-acl spec 158): the Columns tab - filled from the node's reading of an object grant (its test's mode
 * and names, its masks as pairs) and composing the COLUMNS test and MASK items it writes. It reads no text.
 */
public class GrantColumnsTest {
	private static GrantColumns people() {
		return new GrantColumns(List.of(new String[] {"id", "INTEGER"}, new String[] {"ssn", "VARCHAR"},
		    new String[] {"salary", "INTEGER"}, new String[] {"Odd Name", "VARCHAR"}, new String[] {"left", "VARCHAR"}));
	}

	private static Node at(GrantColumns tab, String name) {
		return tab.columns().stream().filter(n -> n.name.equals(name)).findFirst().orElseThrow();
	}

	private static String[] mask(String column, String mask) {
		return new String[] {column, mask};
	}

	@Test
	public void everyColumn() {
		GrantColumns tab = people();
		tab.apply("all", List.of(), List.of());
		assertTrue(tab.columns().stream().allMatch(n -> n.shown == Shown.VISIBLE));
		assertTrue(tab.laterShown());
		assertEquals("", tab.composeColumns());
		assertEquals("", tab.composeMasks());
	}

	@Test
	public void theNodesReadingWrittenBack() {
		GrantColumns tab = people();
		tab.apply("except", List.of("salary", "Odd Name"), List.of(mask("ssn", "CAST('***' AS VARCHAR)"),
		                                                           mask("left", "NULL")));
		assertEquals("* EXCLUDE (salary, \"Odd Name\")", tab.composeColumns());
		assertEquals("ssn AS CAST('***' AS VARCHAR), \"left\" AS NULL", tab.composeMasks());
		assertEquals(Shown.HIDDEN, at(tab, "salary").shown);
		assertEquals(Shown.MASKED, at(tab, "ssn").shown);
		GrantColumns only = people();
		only.apply("only", List.of("id", "odd name"), List.of());
		assertFalse("a list of names hides what it does not name - later columns too", only.laterShown());
		assertEquals(Shown.VISIBLE, at(only, "Odd Name").shown);
		assertEquals(Shown.HIDDEN, at(only, "ssn").shown);
		assertEquals("id, \"Odd Name\"", only.composeColumns());
	}

	@Test
	public void aMaskIsTheTextAsTheNodeHoldsIt() {
		// review: a mask with commas, `AS` or a string of any kind is a value - nothing here splits it
		GrantColumns tab = people();
		String odd = "CASE WHEN x THEN E'a, b AS c' ELSE $$d, e$$ END";
		tab.apply("all", List.of(), List.of(mask("ssn", odd), mask("salary", "0")));
		tab.setShown(at(tab, "id"), Shown.HIDDEN);
		assertEquals("ssn AS " + odd + ", salary AS 0", tab.composeMasks());
	}

	@Test
	public void editsCompose() {
		GrantColumns tab = people();
		tab.apply("all", List.of(), List.of());
		tab.setShown(at(tab, "salary"), Shown.HIDDEN);
		tab.setShown(at(tab, "ssn"), Shown.MASKED);
		assertEquals("a column masked anew reads NULL", "ssn AS NULL", tab.composeMasks());
		at(tab, "ssn").mask = "'***'";
		assertEquals("* EXCLUDE (salary)", tab.composeColumns());
		assertEquals("ssn AS '***'", tab.composeMasks());
		tab.setLaterShown(false);
		assertEquals("a masked column is one the grant shows", "id, ssn, \"Odd Name\", \"left\"", tab.composeColumns());
	}

	@Test
	public void namesAndMasksTheObjectLacksAreKeptInTheirMode() {
		GrantColumns tab = people();
		tab.apply("except", List.of("gone", "salary"), List.of(mask("was", "NULL"), mask("ssn", "NULL")));
		assertEquals("* EXCLUDE (salary, gone)", tab.composeColumns());
		assertEquals("ssn AS NULL, was AS NULL", tab.composeMasks());
		tab.setLaterShown(false);
		assertEquals("id, ssn, \"Odd Name\", \"left\"", tab.composeColumns());
		tab.setLaterShown(true);
		assertEquals("switched back: still hidden when it returns", "* EXCLUDE (salary, gone)", tab.composeColumns());
	}

	@Test
	public void nothingToReadIsRefusedNeverEveryColumn() {
		GrantColumns tab = people();
		tab.apply("only", List.of("id"), List.of());
		Object before = tab.snapshot();
		tab.setShown(at(tab, "id"), Shown.HIDDEN);
		assertThrows(IllegalStateException.class, tab::composeColumns);
		tab.restore(before);
		assertEquals("id", tab.composeColumns());
		GrantColumns masked = people();
		masked.apply("all", List.of(), List.of());
		masked.setShown(at(masked, "ssn"), Shown.MASKED);
		at(masked, "ssn").mask = " "; // cleared in its cell
		assertThrows("a mask is an expression", IllegalStateException.class, masked::composeMasks);
	}

	@Test
	public void theSameObjectIsItsColumns() {
		// review: a new grant's object changed after an edit is another object - the tab starts again from it
		GrantColumns tab = people();
		assertTrue(tab.sameColumns(List.of(new String[] {"id", "INTEGER"}, new String[] {"ssn", "VARCHAR"},
		    new String[] {"salary", "INTEGER"}, new String[] {"Odd Name", "VARCHAR"}, new String[] {"left", "VARCHAR"})));
		assertFalse(tab.sameColumns(List.of(new String[] {"id", "INTEGER"}, new String[] {"card", "VARCHAR"})));
	}
}
