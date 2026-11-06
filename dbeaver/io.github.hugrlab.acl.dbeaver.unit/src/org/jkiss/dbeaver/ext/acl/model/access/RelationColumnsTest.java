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

	/** duckdb-acl spec 150 4a: a keyword-named column keeps its spelling - a quote decides a relation's writability. */
	@Test
	public void keywordColumnsKeepTheirSpelling() {
		RelationColumns table = new RelationColumns(List.of(new String[] {"id", "INTEGER"}, new String[] {"name", "VARCHAR"},
		    new String[] {"Sales", "INTEGER"}));
		for (String list : new String[] {"i = id, n = name", "i = id, s = \"Sales\"", "i = id, s = Sales",
		                                 "i = id, n = \"name\""}) {
			table.apply(list);
			assertEquals(list, table.compose());
		}
		// an edit of another column keeps a stored rename's physical side as written
		table.apply("i = id, n = \"name\"");
		table.columns().get(0).as = "ident";
		assertEquals("ident = id, n = \"name\"", table.compose());
		// a later list forgets it: a new rename is written by the rule
		table.apply("i = id, name");
		table.columns().get(1).as = "n";
		assertEquals("i = id, n = name", table.compose());
		// a list never collapses to none (the alias form - writable): every column as itself stays listed
		table.apply("id, name, \"Sales\" NOT NULL");
		table.columns().get(2).nullable = "";
		assertEquals("id, name, \"Sales\"", table.compose());
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
