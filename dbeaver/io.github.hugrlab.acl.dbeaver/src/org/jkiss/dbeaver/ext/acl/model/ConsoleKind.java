package org.jkiss.dbeaver.ext.acl.model;

/** The connections a console action opens (spec 007.2): each a copy of the user's with one thing changed. */
public enum ConsoleKind {
	/** `acl.mode=manage`: every statement is management (spec 006). */
	MANAGE("manage"),
	/** `acl.mode=native`: plain SQL outside the virtual catalog - the node's passthrough. */
	NATIVE("native"),
	/** The driver's `acl.viewAs` - the mode declared on every call: the catalog as a role sees it (duckdb-acl 120.3). */
	VIEW_AS("view_as");

	private final String id;

	ConsoleKind(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static ConsoleKind of(String id) {
		for (ConsoleKind kind : values()) {
			if (kind.id.equals(id)) {
				return kind;
			}
		}
		throw new IllegalArgumentException("no console kind " + id);
	}
}
