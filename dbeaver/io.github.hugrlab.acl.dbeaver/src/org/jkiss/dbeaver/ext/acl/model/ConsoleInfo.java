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

	/**
	 * Whether an editor's statement can work (spec 008.1): the principal calls the platform function its grammar
	 * compiles to, and - a catalog's administrator without the policy bundle - the catalog is one it administers
	 * (null: the statement belongs to no catalog). Convenience only: the node judges every statement.
	 */
	public boolean mayCall(String function, String catalog) {
		if (!myFunctions.contains(function)) {
			return false;
		}
		if (catalog == null || bundles.contains("policy") || bundles.contains("passthrough") || adminCatalogs.isEmpty()) {
			return true;
		}
		for (String administered : adminCatalogs) {
			if (administered.equalsIgnoreCase(catalog)) { // catalog names fold ASCII, as the node's (spec 116)
				return true;
			}
		}
		return false;
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
