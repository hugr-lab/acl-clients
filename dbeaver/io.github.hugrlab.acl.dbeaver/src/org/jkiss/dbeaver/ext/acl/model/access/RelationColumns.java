package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A virtual table's own column list as the Columns tab edits it (spec 008.2, spec 011 / duckdb-acl spec 158): the
 * physical columns of its source, each exposed or not, under a name, through an expression - and the computed columns
 * the source does not have. Read from and written to the `COLUMNS (…)` text, a SELECT list over the source: `name`,
 * `column AS new_name`, `<expression> AS name` (nullability is the source's). An item it does not take apart - a star,
 * `a.b`, `name: <expr>` - is kept as written, and the items keep their order. A source column a list reads twice is
 * the column once and a computed column after that (`amount, amount AS amount2`).
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

	/** `<left> AS <name>` at the item's end; the name bare or quoted. */
	private static final Pattern AS_NAME = Pattern.compile("(.*\\S)\\s+AS\\s+(\"(?:[^\"]|\"\")+\"|[A-Za-z_][A-Za-z0-9_$]*)",
	                                                       Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	/** One name: a column of the source as written. */
	private static final Pattern NAME = Pattern.compile("\"(?:[^\"]|\"\")+\"|[A-Za-z_][A-Za-z0-9_$]*");

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

	public List<Computed> computed() {
		return computed;
	}

	public List<String> unknown() {
		return unknown;
	}

	/** The source's column a name is, when it is one name. */
	private Column physical(String name) {
		if (!NAME.matcher(name.trim()).matches()) {
			return null;
		}
		String bare = GrantColumns.unquote(name);
		for (Column column : columns) {
			if (column.name.equalsIgnoreCase(bare)) {
				return column;
			}
		}
		return null;
	}

	/** The states a relation's `COLUMNS` text says; empty = every physical column, as it is (the alias form). */
	public void apply(String list) {
		computed.clear();
		unknown.clear();
		order.clear();
		List<String> items = new ArrayList<>();
		for (String item : GrantColumns.split(list == null ? "" : list, ',')) {
			if (!item.trim().isEmpty()) {
				items.add(item.trim());
			}
		}
		listed = !items.isEmpty();
		for (Column column : columns) {
			column.exposed = items.isEmpty();
			column.as = column.name;
			column.expression = "";
		}
		java.util.Set<Column> used = new java.util.HashSet<>();
		for (String item : items) {
			Matcher named = AS_NAME.matcher(item);
			if (!named.matches()) {
				Column column = physical(item);
				if (column == null || !used.add(column)) {
					unknown.add(item); // a star, `a.b`, `name: <expr>` (or a column listed twice): kept as written
					order.add(item);
				} else {
					column.exposed = true;
					order.add(column);
				}
				continue;
			}
			String left = named.group(1).trim();
			String name = GrantColumns.unquote(named.group(2));
			Column renamed = physical(left);
			Column target = physical(named.group(2));
			if (renamed != null && used.add(renamed)) {
				renamed.exposed = true; // column AS new_name
				renamed.as = name;
				order.add(renamed);
			} else if (renamed == null && target != null && used.add(target)) {
				target.exposed = true; // an expression of the source read as the column
				target.expression = left;
				order.add(target);
			} else {
				Computed made = new Computed(name, left);
				computed.add(made);
				order.add(made);
			}
		}
	}

	private static String item(Column column) {
		String as = AclGrammar.fieldIdent(column.as);
		if (!column.expression.isEmpty()) {
			return column.expression + " AS " + as;
		}
		return column.as.equals(column.name) ? as : AclGrammar.fieldIdent(column.name) + " AS " + as;
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
					items.add(made.expression + " AS " + AclGrammar.fieldIdent(made.name));
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
				items.add(made.expression + " AS " + AclGrammar.fieldIdent(made.name));
			}
		}
		return String.join(", ", items);
	}
}
