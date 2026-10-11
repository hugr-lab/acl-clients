package org.jkiss.dbeaver.ext.acl.model;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The words of duckdb-acl's management grammar (website/docs/management-sql.md) that DBeaver does not know as SQL
 * keywords already (AS, VIEW, GRANT, MAP, ... are - adding them again would retype them). Highlighted, never a reason
 * to quote an identifier (AclSQLDialect): `main` and `platform` are names first.
 */
public final class AclKeywords {
	public static final List<String> WORDS = List.of("ACL", "NATIVE", "VIRTUAL", "ROLES", "DENY", "CLAIMS", "CAPS",
	    "RLS", "MAIN", "MASK", "ISSUER", "CLIENT", "AUDIENCES", "CLAIM", "CATEGORY", "RESOURCE", "CLUSTER", "KILL",
	    "DRAIN", "RESUME", "NODE", "REPAIR", "REMAP", "REFERENCE", "OPTIONAL", "PASSTHROUGH", "OBSERVE", "OPERATE",
	    "POLICY", "PLATFORM", "MIGRATE");

	private static final Set<String> SET = Set.copyOf(WORDS);

	private AclKeywords() {
	}

	public static boolean contains(String word) {
		return word != null && SET.contains(word.toUpperCase(Locale.ROOT));
	}
}
