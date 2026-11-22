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
 * Spec 011 (duckdb-acl spec 158): the Columns tab - an object grant's COLUMNS test and MASK items read into the
 * object's columns and written back.
 */
public class GrantColumnsTest {
	private static GrantColumns people() {
		return new GrantColumns(List.of(new String[] {"id", "INTEGER"}, new String[] {"ssn", "VARCHAR"},
		    new String[] {"salary", "INTEGER"}, new String[] {"Odd Name", "VARCHAR"}, new String[] {"left", "VARCHAR"}));
	}

	private static Node at(GrantColumns tab, String name) {
		return tab.columns().stream().filter(n -> n.name.equals(name)).findFirst().orElseThrow();
	}

	@Test
	public void noTestIsEveryColumn() {
		for (String test : new String[] {"", "*", null}) {
			GrantColumns tab = people();
			tab.apply(test, "");
			assertTrue(tab.columns().stream().allMatch(n -> n.shown == Shown.VISIBLE));
			assertTrue(tab.laterShown());
			assertEquals("", tab.composeColumns());
			assertEquals("", tab.composeMasks());
		}
	}

	@Test
	public void theThreeFormsRoundTrip() {
		String[][] cases = {{"", ""}, {"id, salary", ""}, {"* EXCLUDE (salary, \"Odd Name\")", ""},
		                    {"id, ssn", "ssn AS CAST('***' AS VARCHAR)"},
		                    {"* EXCLUDE (salary)", "ssn AS left(ssn, 1) || '***', \"left\" AS NULL"}};
		for (String[] each : cases) {
			GrantColumns tab = people();
			tab.apply(each[0], each[1]);
			assertEquals(each[0], tab.composeColumns());
			assertEquals(each[1], tab.composeMasks());
		}
	}

	@Test
	public void theStates() {
		GrantColumns tab = people();
		tab.apply("* EXCLUDE (salary)", "ssn AS NULL");
		assertEquals(Shown.VISIBLE, at(tab, "id").shown);
		assertEquals(Shown.HIDDEN, at(tab, "salary").shown);
		assertEquals(Shown.MASKED, at(tab, "ssn").shown);
		assertEquals("NULL", at(tab, "ssn").mask);
		tab.apply("id, \"odd name\"", "");
		assertFalse("a list of names hides what it does not name - later columns too", tab.laterShown());
		assertEquals(Shown.VISIBLE, at(tab, "Odd Name").shown);
		assertEquals(Shown.HIDDEN, at(tab, "ssn").shown);
	}

	@Test
	public void editsCompose() {
		GrantColumns tab = people();
		tab.apply("", "");
		tab.setShown(at(tab, "salary"), Shown.HIDDEN);
		tab.setShown(at(tab, "ssn"), Shown.MASKED);
		at(tab, "ssn").mask = "'***'";
		assertEquals("a grant of every column hides by EXCLUDE: a later column stays shown, as it was",
		             "* EXCLUDE (salary)", tab.composeColumns());
		assertEquals("ssn AS '***'", tab.composeMasks());
		tab.setLaterShown(false);
		assertEquals("a masked column is one the grant shows", "id, ssn, \"Odd Name\", \"left\"", tab.composeColumns());
	}

	@Test
	public void namesAndMasksTheObjectLacksAreKept() {
		GrantColumns tab = people();
		tab.apply("* EXCLUDE (gone, salary)", "was AS NULL, ssn AS NULL");
		assertEquals("* EXCLUDE (salary, gone)", tab.composeColumns());
		assertEquals("ssn AS NULL, was AS NULL", tab.composeMasks());
		// in the other mode a name the object lacks says what leaving it out says: it goes
		tab.setLaterShown(false);
		assertEquals("id, ssn, \"Odd Name\", \"left\"", tab.composeColumns());
	}

	@Test
	public void nothingToReadIsRefusedNeverEveryColumn() {
		GrantColumns tab = people();
		tab.apply("id", "");
		tab.setShown(at(tab, "id"), Shown.HIDDEN);
		assertThrows(IllegalStateException.class, tab::composeColumns);
		GrantColumns masked = people();
		masked.apply("", "");
		masked.setShown(at(masked, "ssn"), Shown.MASKED);
		at(masked, "ssn").mask = " "; // cleared in its cell
		assertThrows("a mask is an expression", IllegalStateException.class, masked::composeMasks);
	}

	@Test
	public void switchingTheModeAndBackKeepsWhatTheObjectLacks() {
		// review: a name the object lacks (a column gone from the source) under EXCLUDE must stay hidden when it returns
		GrantColumns tab = people();
		tab.apply("* EXCLUDE (gone)", "");
		tab.setLaterShown(false);
		tab.setLaterShown(true);
		assertEquals("* EXCLUDE (gone)", tab.composeColumns());
		GrantColumns list = people();
		list.apply("id, back", "");
		list.setLaterShown(true);
		list.setLaterShown(false);
		assertEquals("id, back", list.composeColumns());
	}

	@Test
	public void excludeWithoutParentheses() {
		GrantColumns tab = people();
		tab.apply("* EXCLUDE salary", "");
		assertTrue(tab.laterShown());
		assertEquals(Shown.HIDDEN, at(tab, "salary").shown);
		assertEquals(Shown.VISIBLE, at(tab, "id").shown);
		assertEquals("* EXCLUDE (salary)", tab.composeColumns());
	}

	@Test
	public void aColumnMaskedAnewReadsNull() {
		GrantColumns tab = people();
		tab.apply("", "");
		tab.setShown(at(tab, "ssn"), Shown.MASKED);
		assertEquals("ssn AS NULL", tab.composeMasks());
	}
}
