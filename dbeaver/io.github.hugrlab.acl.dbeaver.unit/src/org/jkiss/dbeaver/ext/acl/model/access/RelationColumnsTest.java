package org.jkiss.dbeaver.ext.acl.model.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/** Spec 008.2: a virtual table's Columns tab - its COLUMNS text read into the source's columns and written back. */
public class RelationColumnsTest {
	private static RelationColumns orders() {
		return new RelationColumns(List.of(new String[] {"id", "INTEGER"}, new String[] {"pk", "INTEGER"},
		    new String[] {"amount", "INTEGER"}, new String[] {"ssn", "VARCHAR"}, new String[] {"Odd Name", "VARCHAR"}));
	}

	@Test
	public void noListIsTheAliasForm() {
		RelationColumns table = orders();
		table.apply("");
		assertTrue(table.columns().stream().allMatch(c -> c.exposed));
		assertEquals("", table.compose());
	}

	@Test
	public void roundTrips() {
		for (String list : new String[] {"id, amount", "id = pk, amount", "id NOT NULL, amount, ssn = NULL",
		                                 "id, amount, total = amount * 2", "\"Odd Name\"", "id, address.city",
		                                 "id, ssn = left(ssn, 1) || '***' NOT NULL"}) {
			RelationColumns table = orders();
			table.apply(list);
			assertEquals(list, table.compose());
		}
	}

	@Test
	public void theStates() {
		RelationColumns table = orders();
		table.apply("id = pk, amount NOT NULL, ssn = NULL, total = amount * 2");
		RelationColumns.Column pk = table.columns().get(1);
		assertTrue(pk.exposed);
		assertEquals("a rename", "id", pk.as);
		assertFalse("id itself is not exposed", table.columns().get(0).exposed);
		assertEquals("NOT NULL", table.columns().get(2).nullable);
		assertEquals("a mask", "NULL", table.columns().get(3).expression);
		assertEquals("a computed column", "total", table.computed().get(0).name);
	}

	@Test
	public void editsCompose() {
		RelationColumns table = orders();
		table.apply("");
		table.columns().get(3).expression = "'***'";
		table.columns().get(4).exposed = false;
		table.computed().add(new RelationColumns.Computed("total", "amount * 2"));
		assertEquals("id, pk, amount, ssn = '***', total = amount * 2", table.compose());
	}
}
