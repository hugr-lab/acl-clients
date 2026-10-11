package io.github.hugrlab.acl.dbeaver.model;

import java.util.List;

/** The words of duckdb-acl's management grammar that duckdb's SQL does not have (website/docs/management-sql.md). */
public final class AclKeywords {
	public static final List<String> WORDS = List.of("ACL", "NATIVE", "VIRTUAL", "CATALOG", "ROLE", "ROLES", "GRANT",
	    "REVOKE", "DENY", "CLAIMS", "CAPS", "RLS", "MAIN", "MASK", "ISSUER", "CLIENT", "AUDIENCES", "MAP", "CLAIM",
	    "SOURCE", "CATEGORY", "RESOURCE", "GROUP", "CLUSTER", "SESSION", "KILL", "DRAIN", "RESUME", "NODE", "REPAIR",
	    "REMAP", "CHECK", "REFERENCE", "CARDINALITY", "OPTIONAL", "PASSTHROUGH", "OBSERVE", "OPERATE", "POLICY",
	    "VIEW", "AS", "PLATFORM", "MIGRATE");

	private AclKeywords() {
	}
}
