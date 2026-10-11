package org.jkiss.dbeaver.ext.acl.model;

import java.util.Set;

/**
 * What `platform.console_info()` answers this principal (duckdb-acl spec 117): its admin bundles, the catalogs it
 * administers, the platform views it reads and the platform functions it calls. A console action is shown only where
 * it works; the node judges again whatever is shown.
 */
public record ConsoleInfo(Set<String> bundles, Set<String> adminCatalogs, Set<String> myViews,
                          Set<String> myFunctions) {
	public boolean offers(ConsoleKind kind) {
		return switch (kind) {
			case MANAGE -> !bundles.isEmpty() || !adminCatalogs.isEmpty() || !myViews.isEmpty();
			case NATIVE -> bundles.contains("passthrough");
			case VIEW_AS -> myFunctions.contains("view_as") && myViews.contains("roles");
		};
	}

	public boolean offersAny() {
		for (ConsoleKind kind : ConsoleKind.values()) {
			if (offers(kind)) {
				return true;
			}
		}
		return false;
	}
}
