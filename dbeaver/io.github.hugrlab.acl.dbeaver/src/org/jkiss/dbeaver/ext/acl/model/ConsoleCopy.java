package org.jkiss.dbeaver.ext.acl.model;

import java.util.List;

/**
 * How a console's connection differs from the user's (spec 007.2): its name, the driver's `acl.mode`, the statements
 * every physical connection runs first, read-only, and DBeaver's connection type (its colour) - null keeps the
 * user's. A native console is `prod` (DBeaver confirms what it executes), a view-as connection `test`.
 */
public record ConsoleCopy(String name, String mode, List<String> initQueries, boolean readOnly,
                          String connectionType) {
	public static ConsoleCopy of(ConsoleKind kind, String baseName, String role) {
		return switch (kind) {
			case MANAGE -> new ConsoleCopy(baseName + " · manage", "manage", List.of(), false, null);
			case NATIVE -> new ConsoleCopy(baseName + " · native", "native", List.of(), false, "prod");
			case VIEW_AS -> new ConsoleCopy(baseName + " · as " + role, "data", List.of(viewAs(role)), true, "test");
		};
	}

	/** The view-as statement: the role as a string literal (a quote inside doubled). */
	public static String viewAs(String role) {
		return "SET SESSION VIEW AS ROLE '" + role.replace("'", "''") + "'";
	}
}
