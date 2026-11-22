package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An object grant's columns as the Columns tab edits them (spec 011, duckdb-acl spec 158): each column the object
 * exposes - as the node answers them (`platform.check_grant` with no list, duckdb-acl spec 122) - Visible, Masked (the
 * expression the role reads instead) or Hidden. Filled from what the node read with duckdb's parser -
 * `platform.object_grants`' test (mode, names) and masks (column, mask) - and never from a text: this composes the
 * `COLUMNS` test and the `MASK` items to write, it reads neither. The test's mode is what a column the object gains
 * later is: shown under `* EXCLUDE`, hidden under a list of names. A name or a mask of a column the object no longer has
 * is kept - the node's check says what it is - never dropped.
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

	private final List<Node> columns = new ArrayList<>();
	// names of the test the object lacks, per mode: hidden under EXCLUDE, shown under a list - each kept for its mode,
	// so switching the mode and back keeps them
	private final List<String> unknownExcluded = new ArrayList<>();
	private final List<String> unknownShown = new ArrayList<>();
	private final List<String[]> unknownMasks = new ArrayList<>(); // (column, mask) of a column the object lacks
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

	private Node column(String name) {
		for (Node column : columns) {
			if (column.name.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
				return column;
			}
		}
		return null;
	}

	/**
	 * The states the node's reading of a grant says: its test's mode (`all`, `only`, `except`) and names, and its masks
	 * as (column, mask) pairs.
	 */
	public void apply(String mode, List<String> names, List<String[]> masks) {
		unknownExcluded.clear();
		unknownShown.clear();
		unknownMasks.clear();
		boolean all = mode == null || mode.equals("all");
		laterShown = all || mode.equals("except");
		for (Node column : columns) {
			column.mask = "";
			column.shown = laterShown ? Shown.VISIBLE : Shown.HIDDEN;
		}
		for (String name : all ? List.<String>of() : names) {
			Node column = column(name);
			if (column == null) {
				(laterShown ? unknownExcluded : unknownShown).add(name);
			} else {
				column.shown = laterShown ? Shown.HIDDEN : Shown.VISIBLE;
			}
		}
		for (String[] mask : masks) {
			Node column = column(mask[0]);
			if (column == null) {
				unknownMasks.add(mask);
			} else {
				column.shown = Shown.MASKED; // a mask names a column the grant shows
				column.mask = mask[1];
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

	/** The states as they are now, to put back an edit that writes nothing. */
	public Object snapshot() {
		List<Object> out = new ArrayList<>();
		for (Node column : columns) {
			out.add(new Object[] {column.shown, column.mask});
		}
		out.add(laterShown);
		return out;
	}

	@SuppressWarnings("unchecked")
	public void restore(Object snapshot) {
		List<Object> saved = (List<Object>) snapshot;
		for (int i = 0; i < columns.size(); i++) {
			Object[] state = (Object[]) saved.get(i);
			columns.get(i).shown = (Shown) state[0];
			columns.get(i).mask = (String) state[1];
		}
		laterShown = (Boolean) saved.get(columns.size());
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
		for (String[] mask : unknownMasks) {
			items.add(AclGrammar.fieldIdent(mask[0]) + " AS " + mask[1]);
		}
		return String.join(", ", items);
	}
}
