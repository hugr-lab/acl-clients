package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A virtual table's own column list as the Columns tab edits it (spec 008.2): the physical columns of its source, each
 * exposed or not, under a name, through an expression, with a nullability mark - and the computed columns the source
 * does not have. Read from and written to the `COLUMNS (…)` text (the docs' items: `name`, `new_name = column`, `name =
 * <expression>`, `name NOT NULL` / `name NULL`). An item the table does not know (a field path) is kept as written.
 */
public final class RelationColumns {
	/** One physical column. */
	public static final class Column {
		public final String name;
		public final String type;
		public boolean exposed = true;
		public String as;               // the name the virtual table gives it
		public String expression = "";  // a mask over the physical row; empty = the column itself
		public String nullable = "";    // "", "NOT NULL" or "NULL"

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
		public String nullable = "";

		public Computed(String name, String expression) {
			this.name = name;
			this.expression = expression;
		}
	}

	private final List<Column> columns = new ArrayList<>();
	private final List<Computed> computed = new ArrayList<>();
	private final List<String> unknown = new ArrayList<>();

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

	private Column physical(String name) {
		String bare = unquote(name.trim());
		for (Column column : columns) {
			if (column.name.equalsIgnoreCase(bare)) {
				return column;
			}
		}
		return null;
	}

	private static String unquote(String name) {
		if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
			return name.substring(1, name.length() - 1).replace("\"\"", "\"");
		}
		return name;
	}

	/** `name NOT NULL` / `name NULL`: the mark taken off; [rest, mark]. */
	private static String[] mark(String item) {
		String upper = item.toUpperCase(Locale.ROOT);
		if (upper.endsWith(" NOT NULL")) {
			return new String[] {item.substring(0, item.length() - 9).trim(), "NOT NULL"};
		}
		if (upper.endsWith(" NULL") && !upper.endsWith("= NULL")) {
			return new String[] {item.substring(0, item.length() - 5).trim(), "NULL"};
		}
		return new String[] {item, ""};
	}

	/** The states a relation's `COLUMNS` text says; empty = every physical column, as it is (the alias form). */
	public void apply(String list) {
		computed.clear();
		unknown.clear();
		List<String> items = new ArrayList<>();
		for (String item : GrantColumns.split(list == null ? "" : list, ',')) {
			if (!item.trim().isEmpty()) {
				items.add(item.trim());
			}
		}
		for (Column column : columns) {
			column.exposed = items.isEmpty();
			column.as = column.name;
			column.expression = "";
			column.nullable = "";
		}
		for (String item : items) {
			String[] marked = mark(item);
			List<String> sides = GrantColumns.split(marked[0], '=');
			String left = sides.get(0).trim();
			if (left.contains(".") && !left.startsWith("\"")) {
				unknown.add(item); // a field path (spec 102): kept as written
				continue;
			}
			if (sides.size() == 1) {
				Column column = physical(left);
				if (column == null) {
					unknown.add(item);
				} else {
					column.exposed = true;
					column.nullable = marked[1];
				}
				continue;
			}
			String right = marked[0].substring(marked[0].indexOf('=') + 1).trim();
			Column renamed = physical(right);
			Column target = physical(left);
			if (renamed != null && !right.contains("(") && !right.contains(" ")) {
				renamed.exposed = true; // new_name = column
				renamed.as = unquote(left);
				renamed.nullable = marked[1];
			} else if (target != null) {
				target.exposed = true; // a mask over the physical row
				target.expression = right;
				target.nullable = marked[1];
			} else {
				Computed made = new Computed(unquote(left), right);
				made.nullable = marked[1];
				computed.add(made);
			}
		}
	}

	/** The `COLUMNS` text these states write; empty when every column is exposed as it is (the alias form). */
	public String compose() {
		boolean plain = computed.isEmpty() && unknown.isEmpty();
		for (Column column : columns) {
			plain &= column.exposed && column.as.equals(column.name) && column.expression.isEmpty()
			         && column.nullable.isEmpty();
		}
		if (plain) {
			return "";
		}
		List<String> items = new ArrayList<>();
		for (Column column : columns) {
			if (!column.exposed) {
				continue;
			}
			String item = AclGrammar.ident(column.as);
			if (!column.expression.isEmpty()) {
				item += " = " + column.expression;
			} else if (!column.as.equals(column.name)) {
				item += " = " + AclGrammar.ident(column.name);
			}
			items.add(item + (column.nullable.isEmpty() ? "" : " " + column.nullable));
		}
		for (Computed made : computed) {
			items.add(AclGrammar.ident(made.name) + " = " + made.expression
			          + (made.nullable.isEmpty() ? "" : " " + made.nullable));
		}
		items.addAll(unknown);
		return String.join(", ", items);
	}
}
