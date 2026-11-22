package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An object grant's columns as the Columns tab edits them (spec 011, duckdb-acl spec 158): each column the object
 * exposes - as the node answers them (`platform.check_grant` with no list, duckdb-acl spec 122) - Visible, Masked (the
 * expression the role reads instead) or Hidden. Read from and written to the grant's two texts: its `COLUMNS` test (empty
 * = every column, `a, b`, `* EXCLUDE (a, b)`) and its `MASK` items (`col AS <expr>, …`). The test's mode is what a
 * column the object gains later is: shown under `* EXCLUDE`, hidden under a list of names. A name or a mask of a column
 * the object no longer has is kept as written - the node's check says what it is - never dropped.
 */
public final class GrantColumns {
	public enum Shown {
		VISIBLE, MASKED, HIDDEN
	}

	/** One column of the object. */
	public static final class Node {
		public final String name;
		public final String type;
		public Shown shown = Shown.VISIBLE;
		public String mask = "";

		Node(String name, String type) {
			this.name = name;
			this.type = type;
		}
	}

	// `* EXCLUDE (a, b)`, or duckdb's one-name form `* EXCLUDE a`
	private static final Pattern EXCLUDE = Pattern.compile("\\*\\s*EXCLUDE\\s*(?:\\((.*)\\)|(\\S.*))",
	                                                       Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	private static final Pattern MASK_ITEM = Pattern.compile("(\"(?:[^\"]|\"\")+\"|[^\\s\"]+)\\s+AS\\s+(.+)",
	                                                         Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private final List<Node> columns = new ArrayList<>();
	// names of the test the object lacks, per mode: hidden under EXCLUDE, shown under a list - each kept for its mode,
	// so switching the mode and back keeps them
	private final List<String> unknownExcluded = new ArrayList<>();
	private final List<String> unknownShown = new ArrayList<>();
	private final List<String> unknownMasks = new ArrayList<>(); // mask items as written
	private boolean laterShown = true;

	/** The object's columns - (name, type) as the node answers them. */
	public GrantColumns(List<String[]> exposed) {
		for (String[] column : exposed) {
			columns.add(new Node(column[0], column[1]));
		}
	}

	public List<Node> columns() {
		return columns;
	}

	/** Whether a column the object gains later is shown (`* EXCLUDE`) or hidden (a list of names). */
	public boolean laterShown() {
		return laterShown;
	}

	/**
	 * The mode: a name the object lacks keeps its meaning only in its own mode (hidden under EXCLUDE, shown under a
	 * list) - in the other one leaving it out says the same, so it is written only while its mode is.
	 */
	public void setLaterShown(boolean shown) {
		laterShown = shown;
	}

	/** Split at a delimiter outside quotes and brackets ( ( ), [ ], { } ). */
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
			} else if (c == '(' || c == '[' || c == '{') {
				depth++;
				part.append(c);
			} else if (c == ')' || c == ']' || c == '}') {
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

	static String unquote(String name) {
		String bare = name.trim();
		if (bare.length() >= 2 && bare.startsWith("\"") && bare.endsWith("\"")) {
			return bare.substring(1, bare.length() - 1).replace("\"\"", "\"");
		}
		return bare;
	}

	private static List<String> names(String list) {
		List<String> out = new ArrayList<>();
		for (String item : split(list, ',')) {
			if (!item.trim().isEmpty()) {
				out.add(unquote(item));
			}
		}
		return out;
	}

	private Node column(String name) {
		for (Node column : columns) {
			if (column.name.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
				return column;
			}
		}
		return null;
	}

	/** The states a grant's `COLUMNS` test and `MASK` items say; both empty = every column, unmasked. */
	public void apply(String test, String masks) {
		unknownExcluded.clear();
		unknownShown.clear();
		unknownMasks.clear();
		String text = test == null ? "" : test.trim();
		Matcher except = EXCLUDE.matcher(text);
		boolean all = text.isEmpty() || text.equals("*");
		laterShown = all || except.matches();
		String exceptNames = except.matches() ? (except.group(1) != null ? except.group(1) : except.group(2)) : text;
		List<String> named = all ? List.of() : names(exceptNames);
		for (Node column : columns) {
			column.mask = "";
			column.shown = laterShown ? Shown.VISIBLE : Shown.HIDDEN;
		}
		for (String name : named) {
			Node column = column(name);
			if (column == null) {
				(laterShown ? unknownExcluded : unknownShown).add(name);
			} else {
				column.shown = laterShown ? Shown.HIDDEN : Shown.VISIBLE;
			}
		}
		for (String item : split(masks == null ? "" : masks, ',')) {
			if (item.trim().isEmpty()) {
				continue;
			}
			Matcher m = MASK_ITEM.matcher(item.trim());
			Node column = m.matches() ? column(unquote(m.group(1))) : null;
			if (column == null) {
				unknownMasks.add(item.trim());
			} else {
				column.shown = Shown.MASKED; // a mask names a column the grant shows
				column.mask = m.group(2).trim();
			}
		}
	}

	/** An edit of one column, as the tab makes it: a column masked anew reads NULL until its mask is written. */
	public void setShown(Node node, Shown shown) {
		node.shown = shown;
		if (shown == Shown.MASKED && (node.mask == null || node.mask.trim().isEmpty())) {
			node.mask = "NULL";
		}
	}

	/**
	 * The `COLUMNS` test these states write: empty = every column (nothing hidden, later columns shown). A list of
	 * names with every column hidden says nothing to read; that is refused, never written as every column.
	 */
	public String composeColumns() {
		List<String> shown = new ArrayList<>();
		List<String> hidden = new ArrayList<>();
		for (Node column : columns) {
			(column.shown == Shown.HIDDEN ? hidden : shown).add(AclGrammar.fieldIdent(column.name));
		}
		for (String name : laterShown ? unknownExcluded : unknownShown) {
			(laterShown ? hidden : shown).add(AclGrammar.fieldIdent(name));
		}
		if (laterShown) {
			return hidden.isEmpty() ? "" : "* EXCLUDE (" + String.join(", ", hidden) + ")";
		}
		if (shown.isEmpty()) {
			throw new IllegalStateException("every column is hidden: revoke the grant, or take its select away");
		}
		return String.join(", ", shown);
	}

	/** The `MASK` items these states write (`col AS <expr>, …`); empty = no mask. */
	public String composeMasks() {
		List<String> items = new ArrayList<>();
		for (Node column : columns) {
			if (column.shown == Shown.MASKED) {
				if (column.mask == null || column.mask.trim().isEmpty()) {
					throw new IllegalStateException("the mask of " + column.name + " is empty");
				}
				items.add(AclGrammar.fieldIdent(column.name) + " AS " + column.mask.trim());
			}
		}
		items.addAll(unknownMasks);
		return String.join(", ", items);
	}
}
