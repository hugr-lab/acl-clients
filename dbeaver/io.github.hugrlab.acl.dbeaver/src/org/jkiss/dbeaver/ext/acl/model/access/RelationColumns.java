package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.List;

/**
 * A virtual table's own column list as the Columns tab edits it (spec 008.2, spec 011 / duckdb-acl spec 158): the
 * physical columns of its source, each exposed or not, under a name, through an expression - and the computed columns
 * the source does not have. Filled from the node's reading of the list (`platform.relation_columns`, duckdb's parser)
 * and written as the `COLUMNS (…)` text, a SELECT list over the source: `name`, `column AS new_name`, `<expression>
 * AS name` (nullability is the source's); a star item is kept as the node holds it, the items keep their order, and a
 * source column read twice is the column once and a computed column after that (`amount, amount AS amount2`).
 */
public final class RelationColumns {
	/** One physical column. */
	public static final class Column {
		public final String name;
		public final String type;
		public boolean exposed = true;
		public String as;               // the name the virtual table gives it
		public String expression = "";  // an expression of the source the column is read as; empty = the column itself

		Column(String name, String type) {
			this.name = name;
			this.type = type;
			this.as = name;
		}
	}

	/** A column the source does not have: a name and the expression computing it. */
	public static final class Computed {
		public String name;
		public String expression;

		public Computed(String name, String expression) {
			this.name = name;
			this.expression = expression;
		}
	}

	private final List<Column> columns = new ArrayList<>();
	private final List<Computed> computed = new ArrayList<>();
	private final List<String> unknown = new ArrayList<>();
	// the list's items in its order (a Column, a Computed or an unknown item's text): the order is the table's column
	// order, which `SELECT *` and a positional INSERT read - written back as it was, what an edit adds after it
	private final List<Object> order = new ArrayList<>();
	// whether the applied text was a list: a relation with one never goes back to none by an edit - no list is the
	// alias form (writable, every source column as it comes), a list of every column as itself is read-only
	private boolean listed;

	/** The source's columns - (name, type) as the node lists them. */
	public RelationColumns(List<String[]> physical) {
		for (String[] column : physical) {
			columns.add(new Column(column[0], column[1]));
		}
	}

	public List<Column> columns() {
		return columns;
	}

	/** Whether these are the columns of a source of these (name, type): the same source. */
	public boolean sameColumns(List<String[]> physical) {
		if (physical.size() != columns.size()) {
			return false;
		}
		for (int i = 0; i < physical.size(); i++) {
			if (!physical.get(i)[0].equals(columns.get(i).name)) {
				return false;
			}
		}
		return true;
	}

	public List<Computed> computed() {
		return computed;
	}

	public List<String> unknown() {
		return unknown;
	}

	/** The source's column of this name. */
	private Column physical(String name) {
		for (Column column : columns) {
			if (column.name.equalsIgnoreCase(name)) {
				return column;
			}
		}
		return null;
	}

	/** The source's column an expression is when the node printed it as one column reference (`a` or `"a"`). */
	private Column reference(String expr) {
		for (Column column : columns) {
			// names compare as duckdb compares them: in any case, quoted or not
			if (expr.equalsIgnoreCase(column.name) || expr.equalsIgnoreCase("\"" + column.name.replace("\"", "\"\"") + "\"")) {
				return column;
			}
		}
		return null;
	}

	/**
	 * The states the node's reading of a relation's list says - its `platform.relation_columns` rows in order, each
	 * (name, expr): `expr` empty = the column itself, a NULL name = a star item (its text in `expr`). No rows = every
	 * physical column, as it is (the alias form). Nothing here reads a text: duckdb's parser did, where it was written.
	 */
	public void apply(List<String[]> rows) {
		computed.clear();
		unknown.clear();
		order.clear();
		listed = !rows.isEmpty();
		for (Column column : columns) {
			column.exposed = rows.isEmpty();
			column.as = column.name;
			column.expression = "";
		}
		java.util.Set<Column> used = new java.util.HashSet<>();
		for (String[] row : rows) {
			String name = row[0];
			String expr = row[1] == null ? "" : row[1];
			if (name == null) {
				unknown.add(expr); // a star item, as the node keeps it
				order.add(expr);
				continue;
			}
			Column renamed = expr.isEmpty() ? physical(name) : reference(expr);
			Column target = physical(name);
			if (renamed != null && used.add(renamed)) {
				renamed.exposed = true; // the column itself, or column AS new_name
				renamed.as = expr.isEmpty() ? renamed.name : name;
				order.add(renamed);
			} else if (renamed == null && !expr.isEmpty() && target != null && used.add(target)) {
				target.exposed = true; // an expression of the source read as the column
				target.expression = expr;
				order.add(target);
			} else {
				// a column the source lacks, a computed one, or a source column read twice
				Computed made = new Computed(name, expr.isEmpty() ? AclGrammar.fieldIdent(name) : expr);
				computed.add(made);
				order.add(made);
			}
		}
	}

	/** The `COLUMNS` text these states write; empty when every column is exposed as it is (the alias form). */
	public String compose() {
		boolean plain = computed.isEmpty() && unknown.isEmpty();
		for (Column column : columns) {
			plain &= column.exposed && column.as.equals(column.name) && column.expression.isEmpty();
		}
		if (plain && !listed) {
			return "";
		}
		List<String> items = new ArrayList<>();
		for (Object entry : order) {
			if (entry instanceof Column column) {
				if (column.exposed) {
					items.add(item(column));
				}
			} else if (entry instanceof Computed made) {
				if (computed.contains(made)) { // one the tab removed is gone
					items.add(computedItem(made));
				}
			} else {
				items.add((String) entry);
			}
		}
		for (Column column : columns) { // exposed by an edit: after the list's items
			if (column.exposed && !order.contains(column)) {
				items.add(item(column));
			}
		}
		for (Computed made : computed) { // added by an edit
			if (!order.contains(made)) {
				items.add(computedItem(made));
			}
		}
		return String.join(", ", items);
	}

	private static String computedItem(Computed made) {
		String as = AclGrammar.fieldIdent(made.name);
		return made.expression.equals(as) ? as : made.expression + " AS " + as;
	}

	private static String item(Column column) {
		String as = AclGrammar.fieldIdent(column.as);
		if (!column.expression.isEmpty()) {
			return column.expression + " AS " + as;
		}
		return column.as.equals(column.name) ? as : AclGrammar.fieldIdent(column.name) + " AS " + as;
	}
}
