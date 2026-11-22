package org.jkiss.dbeaver.ext.acl.model.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * Spec 008.2, spec 011 (duckdb-acl spec 158): a virtual table's Columns tab - filled from the node's reading of its
 * list (platform.relation_columns rows: name, expr) and composing the COLUMNS text, a SELECT list, it writes.
 */
public class RelationColumnsTest {
	private static RelationColumns orders() {
		return new RelationColumns(List.of(new String[] {"id", "INTEGER"}, new String[] {"pk", "INTEGER"},
		    new String[] {"amount", "INTEGER"}, new String[] {"ssn", "VARCHAR"}, new String[] {"Odd Name", "VARCHAR"}));
	}

	/** Rows as the node lists them: pairs name, expr (`""` = the column itself, a null name = a star item). */
	private static List<String[]> rows(String... pairs) {
		List<String[]> out = new ArrayList<>();
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			out.add(new String[] {pairs[i], pairs[i + 1]});
		}
		return out;
	}

	@Test
	public void noListIsTheAliasForm() {
		RelationColumns table = orders();
		table.apply(List.of());
		assertTrue(table.columns().stream().allMatch(c -> c.exposed));
		assertEquals("", table.compose());
	}

	@Test
	public void theNodesRowsWrittenBack() {
		RelationColumns table = orders();
		// as the node stored `id, pk AS ident, upper(ssn) AS ssn, amount * 2 AS total, * EXCLUDE (x), addr.city`
		table.apply(rows("id", "", "ident", "pk", "ssn", "upper(ssn)", "total", "(amount * 2)", null, "* EXCLUDE (x)",
		                 "city", "addr.city"));
		assertEquals("id, pk AS ident, upper(ssn) AS ssn, (amount * 2) AS total, * EXCLUDE (x), addr.city AS city",
		             table.compose());
		RelationColumns.Column pk = table.columns().get(1);
		assertTrue(pk.exposed);
		assertEquals("a rename", "ident", pk.as);
		assertEquals("an expression of the source read as the column", "upper(ssn)", table.columns().get(3).expression);
		assertFalse(table.columns().get(2).exposed);
	}

	@Test
	public void aQuotedReferenceIsTheColumn() {
		// the node prints a column reference quoted when duckdb's writer does (`"name"`): the same column
		RelationColumns table = orders();
		table.apply(rows("full", "\"Odd Name\"", "id", ""));
		assertEquals("Odd Name", table.columns().get(4).name);
		assertEquals("full", table.columns().get(4).as);
		assertEquals("\"Odd Name\" AS \"full\", id", table.compose());
	}

	@Test
	public void theListKeepsItsOrderAndEveryItem() {
		RelationColumns table = orders();
		table.apply(rows("amount", "", "amount2", "amount"));
		assertEquals("a column read twice is two columns", "amount, amount AS amount2", table.compose());
		table.apply(rows("amount", "", "id", ""));
		table.columns().get(1).exposed = true; // pk, exposed by an edit: after the list's items
		table.computed().add(new RelationColumns.Computed("total", "amount * 2"));
		assertEquals("amount, id, pk, amount * 2 AS total", table.compose());
		// a list never collapses to none (the alias form - writable): every column as itself stays listed
		table.apply(rows("id", "", "pk", "", "amount", "", "ssn", "", "Odd Name", ""));
		assertEquals("id, pk, amount, ssn, \"Odd Name\"", table.compose());
	}

	@Test
	public void editsCompose() {
		RelationColumns table = orders();
		table.apply(List.of());
		table.columns().get(3).expression = "'***'";
		table.columns().get(4).exposed = false;
		table.columns().get(1).as = "ident";
		assertEquals("id, pk AS ident, amount, '***' AS ssn", table.compose());
	}
}
