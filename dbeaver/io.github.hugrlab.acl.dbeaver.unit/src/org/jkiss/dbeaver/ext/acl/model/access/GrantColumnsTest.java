package org.jkiss.dbeaver.ext.acl.model.access;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.jkiss.dbeaver.ext.acl.model.access.GrantColumns.Node;
import org.jkiss.dbeaver.ext.acl.model.access.GrantColumns.Shown;
import org.junit.Test;

/** Spec 008.1 addendum: the Columns tab's tree - read from a grant's COLUMNS text and written back to it. */
public class GrantColumnsTest {
	private static GrantColumns orders() {
		return new GrantColumns(List.of(new String[] {"id", "INTEGER"}, new String[] {"ssn", "VARCHAR"},
		    new String[] {"address", "STRUCT(city VARCHAR, zip VARCHAR, geo STRUCT(lat DOUBLE, lon DOUBLE))"},
		    new String[] {"items", "STRUCT(price DOUBLE, \"odd name\" VARCHAR)[]"},
		    new String[] {"Mixed", "INTEGER"}));
	}

	private static Node at(GrantColumns tree, String... names) {
		List<Node> level = tree.columns();
		Node found = null;
		for (String name : names) {
			found = level.stream().filter(n -> n.name.equals(name)).findFirst().orElseThrow();
			level = found.children;
		}
		return found;
	}

	@Test
	public void theTreeFromTheTypes() {
		GrantColumns tree = orders();
		assertEquals("address.geo.lat", at(tree, "address", "geo", "lat").path);
		assertEquals("items[].price", at(tree, "items", "price").path);
		assertEquals("items[].\"odd name\"", at(tree, "items", "odd name").path);
		assertEquals("\"Mixed\"", at(tree, "Mixed").path);
	}

	@Test
	public void noListIsEverythingWhole() {
		GrantColumns tree = orders();
		tree.apply("");
		assertEquals(Shown.VISIBLE, at(tree, "address", "geo", "lon").shown);
		assertEquals("", tree.compose());
	}

	@Test
	public void roundTrips() {
		for (String list : new String[] {"id, ssn = NULL", "id, address", "id, address.city, address.geo.lat",
		                                 "id, address, address.zip = '***'", "items[].price", "\"Mixed\"",
		                                 "id, ssn = left(ssn, 1) || '***'"}) {
			GrantColumns tree = orders();
			tree.apply(list);
			assertEquals(list, tree.compose());
		}
	}

	@Test
	public void statesFromTheList() {
		GrantColumns tree = orders();
		tree.apply("id, address.city, ssn = NULL");
		assertEquals(Shown.VISIBLE, at(tree, "id").shown);
		assertEquals(Shown.MASKED, at(tree, "ssn").shown);
		assertEquals("NULL", at(tree, "ssn").mask);
		assertEquals("a listed field keeps its struct", Shown.VISIBLE, at(tree, "address").shown);
		assertEquals(Shown.VISIBLE, at(tree, "address", "city").shown);
		assertEquals(Shown.HIDDEN, at(tree, "address", "zip").shown);
		assertEquals(Shown.HIDDEN, at(tree, "items").shown);
		assertEquals("names compare as the node compares them", Shown.HIDDEN, at(tree, "Mixed").shown);
	}

	@Test
	public void editsCompose() {
		GrantColumns tree = orders();
		tree.apply("");
		at(tree, "ssn").shown = Shown.MASKED;
		at(tree, "ssn").mask = "'***'";
		at(tree, "items").shown = Shown.HIDDEN;
		at(tree, "address", "geo").shown = Shown.HIDDEN;
		assertEquals("id, ssn = '***', address.city, address.zip, \"Mixed\"", tree.compose());
		at(tree, "address", "geo").shown = Shown.VISIBLE;
		at(tree, "address", "zip").shown = Shown.MASKED;
		at(tree, "address", "zip").mask = "NULL";
		assertEquals("a masked field of the whole struct", "id, ssn = '***', address, address.zip = NULL, \"Mixed\"",
		             tree.compose());
	}

	@Test
	public void anEditKeepsTheTreeConsistent() {
		GrantColumns tree = orders();
		tree.apply("id");
		tree.setShown(at(tree, "address", "geo", "lat"), Shown.VISIBLE);
		assertEquals("a field shown keeps its structs", "id, address.geo.lat", tree.compose());
		tree.setShown(at(tree, "items"), Shown.VISIBLE);
		assertEquals("a struct shown with every field hidden is shown whole", "id, address.geo.lat, items", tree.compose());
		tree.setShown(at(tree, "address"), Shown.HIDDEN);
		assertEquals("id, items", tree.compose());
	}

	@Test
	public void whatTheTreeDoesNotKnowIsKept() {
		GrantColumns tree = orders();
		tree.apply("id, gone, total = id * 2");
		assertEquals(List.of("gone", "total = id * 2"), tree.unknown());
		assertEquals("id, gone, total = id * 2", tree.compose());
	}
}
