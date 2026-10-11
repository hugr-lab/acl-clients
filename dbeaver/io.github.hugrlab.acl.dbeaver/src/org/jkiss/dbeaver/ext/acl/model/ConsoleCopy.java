package org.jkiss.dbeaver.ext.acl.model;

/**
 * How a console's connection differs from the user's (spec 007.2): its name, the driver's `acl.mode`, the role the
 * driver's `acl.viewAs` declares on every call (a refusal fails the connect - never the viewer's own view under the
 * role's label), read-only, DBeaver's connection type (its colour) - null keeps the user's -, and whether the user's own
 * bootstrap statements are dropped (a console would prefix them). A console runs in auto-commit (a management or native
 * statement is not held for a commit); a native console is `prod` (DBeaver confirms what it executes), a view-as
 * connection `test`.
 */
public record ConsoleCopy(String name, String mode, String viewAs, boolean readOnly, String connectionType,
                          boolean dropInitQueries, Boolean autoCommit) {
	public static ConsoleCopy of(ConsoleKind kind, String baseName, String role) {
		return switch (kind) {
			case MANAGE -> new ConsoleCopy(baseName + " · manage", "manage", null, false, null, true, true);
			case NATIVE -> new ConsoleCopy(baseName + " · native", "native", null, false, "prod", true, true);
			case VIEW_AS -> new ConsoleCopy(baseName + " · as " + role, "data", role, true, "test", false, null);
		};
	}

	/**
	 * Why a role cannot be viewed from here, or null: DBeaver substitutes `${...}` in a connection's properties, so
	 * such a name would reach the node as another.
	 */
	public static String unviewable(String role) {
		return role.contains("${") ? "a role named with ${...} cannot be viewed from DBeaver (it substitutes it)" : null;
	}
}
