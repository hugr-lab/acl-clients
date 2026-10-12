package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An object grant's column list as a tree the Columns tab edits (spec 008.1 addendum): the columns the object exposes
 * - as the node answers them (`platform.check_grant` with no list, duckdb-acl spec 122), a STRUCT's fields and a list
 * of structs' (`items[].price`) as children - each shown, masked or hidden. Read from the grant's `COLUMNS` text and
 * written back to it, spec 102's forms: a bare `address` is the whole struct, `address.city` narrows it, `address,
 * address.ssn = '***'` masks a field of the whole one. An item the tree does not know is kept as written - the node's
 * check says what it is - never dropped.
 */
public final class GrantColumns {
	public enum Shown {
		VISIBLE, MASKED, HIDDEN
	}

	/** One column or field. */
	public static final class Node {
		public final String name;
		public final String path;  // as COLUMNS writes it: address.geo.lat, items[].price
		public final String type;
		public final boolean list; // a LIST of structs: its fields are reached through `[]`
		public final List<Node> children = new ArrayList<>();
		public Shown shown = Shown.VISIBLE;
		public String mask = "";

		Node(String name, String path, String type, boolean list) {
			this.name = name;
			this.path = path;
			this.type = type;
			this.list = list;
		}

		boolean wholeVisible() {
			if (shown != Shown.VISIBLE) {
				return false;
			}
			for (Node child : children) {
				if (!child.wholeVisible()) {
					return false;
				}
			}
			return true;
		}
	}

	private final List<Node> columns = new ArrayList<>();
	private final List<String> unknown = new ArrayList<>();

	/** The object's columns - (name, type) as the node answers them. */
	public GrantColumns(List<String[]> exposed) {
		for (String[] column : exposed) {
			columns.add(node(column[0], AclGrammar.ident(column[0]), column[1]));
		}
	}

	public List<Node> columns() {
		return columns;
	}

	/** The items of the list the tree does not know (kept as written). */
	public List<String> unknown() {
		return unknown;
	}

	private static Node node(String name, String path, String type) {
		String inner = type == null ? "" : type.trim();
		boolean list = false;
		if (inner.endsWith("[]")) {
			list = true;
			inner = inner.substring(0, inner.length() - 2).trim();
		}
		Node made = new Node(name, path, type, list);
		if (inner.toUpperCase(Locale.ROOT).startsWith("STRUCT(") && inner.endsWith(")")) {
			for (String field : split(inner.substring(7, inner.length() - 1), ',')) {
				String[] nameType = fieldNameType(field.trim());
				if (nameType != null) {
					String child = path + (list ? "[]" : "") + "." + AclGrammar.ident(nameType[0]);
					made.children.add(node(nameType[0], child, nameType[1]));
				}
			}
		}
		return made;
	}

	/** `name TYPE` / `"odd name" TYPE` of a STRUCT's field. */
	private static String[] fieldNameType(String field) {
		if (field.startsWith("\"")) {
			int end = 1;
			StringBuilder name = new StringBuilder();
			while (end < field.length()) {
				char c = field.charAt(end);
				if (c == '"' && end + 1 < field.length() && field.charAt(end + 1) == '"') {
					name.append('"');
					end += 2;
				} else if (c == '"') {
					break;
				} else {
					name.append(c);
					end++;
				}
			}
			return end < field.length() ? new String[] {name.toString(), field.substring(end + 1).trim()} : null;
		}
		int space = field.indexOf(' ');
		return space < 0 ? null : new String[] {field.substring(0, space), field.substring(space + 1).trim()};
	}

	/** Split at a delimiter outside quotes and brackets. */
	static List<String> split(String text, char delimiter) {
		List<String> out = new ArrayList<>();
		int depth = 0;
		char quote = 0;
		StringBuilder part = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quote != 0) {
				part.append(c);
				if (c == quote) {
					if (i + 1 < text.length() && text.charAt(i + 1) == quote) {
						part.append(text.charAt(++i));
					} else {
						quote = 0;
					}
				}
			} else if (c == '\'' || c == '"') {
				quote = c;
				part.append(c);
			} else if (c == '(' || c == '[') {
				depth++;
				part.append(c);
			} else if (c == ')' || c == ']') {
				depth--;
				part.append(c);
			} else if (c == delimiter && depth == 0) {
				out.add(part.toString());
				part.setLength(0);
			} else {
				part.append(c);
			}
		}
		if (part.length() > 0 || !out.isEmpty()) {
			out.add(part.toString());
		}
		return out;
	}

	private Map<String, Node> byPath() {
		Map<String, Node> out = new LinkedHashMap<>();
		List<Node> todo = new ArrayList<>(columns);
		while (!todo.isEmpty()) {
			Node each = todo.remove(0);
			out.put(key(each.path), each);
			todo.addAll(each.children);
		}
		return out;
	}

	/** A path compared as the node compares names: case folded, a part's quotes taken off when it needs none. */
	private static String key(String path) {
		StringBuilder out = new StringBuilder();
		for (String part : split(path.replace("[]", ""), '.')) {
			String bare = part.trim();
			if (bare.startsWith("\"") && bare.endsWith("\"") && bare.length() >= 2) {
				bare = bare.substring(1, bare.length() - 1).replace("\"\"", "\"");
			}
			out.append(out.length() == 0 ? "" : "\u0001").append(bare.toLowerCase(Locale.ROOT));
		}
		return out.toString();
	}

	private static void setAll(Node node, Shown shown) {
		node.shown = shown;
		for (Node child : node.children) {
			setAll(child, shown);
		}
	}

	/** The states a grant's `COLUMNS` text says; empty = every column, whole. */
	public void apply(String list) {
		unknown.clear();
		List<String> items = new ArrayList<>();
		for (String item : split(list == null ? "" : list, ',')) {
			if (!item.trim().isEmpty()) {
				items.add(item.trim());
			}
		}
		for (Node column : columns) {
			setAll(column, items.isEmpty() ? Shown.VISIBLE : Shown.HIDDEN);
			column.mask = "";
		}
		if (items.isEmpty()) {
			return;
		}
		Map<String, Node> nodes = byPath();
		Map<String, Node> parents = new LinkedHashMap<>();
		for (Node column : columns) {
			index(column, null, parents);
		}
		List<Node> touched = new ArrayList<>();
		List<String[]> masks = new ArrayList<>();
		for (String item : items) {
			List<String> sides = split(item, '=');
			Node node = nodes.get(key(sides.get(0).trim()));
			if (node == null) {
				unknown.add(item);
			} else if (sides.size() > 1) {
				masks.add(new String[] {sides.get(0).trim(), item.substring(item.indexOf('=') + 1).trim()});
			} else {
				setAll(node, Shown.VISIBLE); // a bare struct is the whole of it
				touched.add(node);
			}
		}
		for (String[] mask : masks) {
			Node node = nodes.get(key(mask[0]));
			node.shown = Shown.MASKED;
			node.mask = mask[1];
			touched.add(node);
		}
		for (Node node : touched) { // a listed field keeps the column (and the structs above it) in the read
			for (Node up = parents.get(node.path); up != null; up = parents.get(up.path)) {
				if (up.shown == Shown.HIDDEN) {
					up.shown = Shown.VISIBLE;
				}
			}
		}
	}

	private static void index(Node node, Node parent, Map<String, Node> parents) {
		if (parent != null) {
			parents.put(node.path, parent);
		}
		for (Node child : node.children) {
			index(child, node, parents);
		}
	}

	/**
	 * An edit of one node, as the tab makes it: a field shown or masked keeps the structs above it in the read; a
	 * struct shown again whose fields were all hidden is shown whole.
	 */
	public void setShown(Node node, Shown shown) {
		node.shown = shown;
		if (shown == Shown.HIDDEN) {
			return;
		}
		Map<String, Node> parents = new LinkedHashMap<>();
		for (Node column : columns) {
			index(column, null, parents);
		}
		for (Node up = parents.get(node.path); up != null; up = parents.get(up.path)) {
			if (up.shown == Shown.HIDDEN) {
				up.shown = Shown.VISIBLE;
			}
		}
		if (shown == Shown.VISIBLE && !node.children.isEmpty() && allHidden(node)) {
			setAll(node, Shown.VISIBLE);
		}
	}

	private static boolean allHidden(Node node) {
		for (Node child : node.children) {
			if (child.shown != Shown.HIDDEN || !allHidden(child)) {
				return false;
			}
		}
		return true;
	}

	/** The `COLUMNS` text these states write; empty when nothing is hidden or masked (the grant narrows nothing). */
	public String compose() {
		boolean whole = unknown.isEmpty();
		for (Node column : columns) {
			whole &= column.wholeVisible();
		}
		if (whole) {
			return "";
		}
		List<String> items = new ArrayList<>();
		for (Node column : columns) {
			compose(column, items);
		}
		items.addAll(unknown);
		return String.join(", ", items);
	}

	private static void compose(Node node, List<String> items) {
		switch (node.shown) {
			case HIDDEN -> {
			}
			case MASKED -> items.add(node.path + " = " + node.mask);
			case VISIBLE -> {
				if (node.children.isEmpty() || node.wholeVisible()) {
					items.add(node.path);
					return;
				}
				if (!anyHidden(node)) { // the whole struct, a field masked in it
					items.add(node.path);
					masksOf(node, items);
					return;
				}
				for (Node child : node.children) { // narrowed: the fields it keeps
					compose(child, items);
				}
			}
		}
	}

	private static boolean anyHidden(Node node) {
		for (Node child : node.children) {
			if (child.shown == Shown.HIDDEN || anyHidden(child)) {
				return true;
			}
		}
		return false;
	}

	private static void masksOf(Node node, List<String> items) {
		for (Node child : node.children) {
			if (child.shown == Shown.MASKED) {
				items.add(child.path + " = " + child.mask);
			} else {
				masksOf(child, items);
			}
		}
	}
}
