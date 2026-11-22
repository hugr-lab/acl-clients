package org.jkiss.dbeaver.ext.acl.model.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/**
 * Spec 008.2, spec 011 (duckdb-acl spec 158): a virtual table's Columns tab - its COLUMNS text, a SELECT list over the
 * source, read into the source's columns and written back.
 */
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
		for (String list : new String[] {"id, amount", "pk AS id, amount", "id, amount, NULL AS ssn",
		                                 "id, amount, amount * 2 AS total", "\"Odd Name\"", "id, address.city",
		                                 "id, left(ssn, 1) || '***' AS ssn", "* EXCLUDE (ssn)", "id, total: amount + 1",
		                                 "id, CAST(amount AS VARCHAR) AS amount"}) {
			RelationColumns table = orders();
			table.apply(list);
			assertEquals(list, table.compose());
		}
	}

	@Test
	public void theStates() {
		RelationColumns table = orders();
		table.apply("pk AS id, amount, upper(ssn) AS ssn, amount * 2 AS total");
		RelationColumns.Column pk = table.columns().get(1);
		assertTrue(pk.exposed);
		assertEquals("a rename", "id", pk.as);
		assertFalse("id itself is not exposed", table.columns().get(0).exposed);
		assertEquals("an expression of the source read as the column", "upper(ssn)", table.columns().get(3).expression);
		assertEquals("a computed column", "total", table.computed().get(0).name);
		assertEquals("amount * 2", table.computed().get(0).expression);
	}

	@Test
	public void editsCompose() {
		RelationColumns table = orders();
		table.apply("");
		table.columns().get(3).expression = "'***'";
		table.columns().get(4).exposed = false;
		table.columns().get(1).as = "ident";
		table.computed().add(new RelationColumns.Computed("total", "amount * 2"));
		assertEquals("id, pk AS ident, amount, '***' AS ssn, amount * 2 AS total", table.compose());
		// a list never collapses to none (the alias form - writable): every column as itself stays listed
		table.apply("id, pk, amount, ssn, \"Odd Name\"");
		assertEquals("id, pk, amount, ssn, \"Odd Name\"", table.compose());
	}

	@Test
	public void aFunctionOrTypeKeywordIsQuoted() {
		// duckdb-acl spec 150 4f: the node reads a list's name as duckdb's column name, which `left` is not
		RelationColumns table = new RelationColumns(List.of(new String[] {"id", "INTEGER"}, new String[] {"left", "VARCHAR"},
		    new String[] {"name", "VARCHAR"}));
		table.apply("");
		table.columns().get(1).expression = "'***'";
		assertEquals("id, '***' AS \"left\", name", table.compose());
		assertEquals("\"full\"", AclGrammar.fieldIdent("full"));
		assertEquals("\"at\"", AclGrammar.fieldIdent("at"));
		assertEquals("name", AclGrammar.fieldIdent("name"));
	}

	@Test
	public void theListKeepsItsOrderAndEveryItem() {
		// review: the list's order is the table's column order (SELECT *, a positional INSERT), and a column read twice
		// is two columns
		for (String list : new String[] {"amount, amount AS amount2", "ssn AS full_name, id",
		                                 "* EXCLUDE (ssn), upper(ssn) AS ssn_u", "amount, id, pk"}) {
			RelationColumns table = orders();
			table.apply(list);
			assertEquals(list, table.compose());
		}
		RelationColumns table = orders();
		table.apply("amount, id");
		table.columns().get(1).exposed = true; // pk, exposed by an edit: after the list's items
		table.computed().add(new RelationColumns.Computed("total", "amount * 2"));
		assertEquals("amount, id, pk, amount * 2 AS total", table.compose());
	}
}
