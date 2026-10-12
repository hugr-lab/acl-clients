package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * A role (platform.roles). Read: one property per column of its view (spec 007.3). Written (spec 008.1): `CREATE ROLE r
 * [CLAIMS (…)]`, then `ALTER ROLE r SET COMMENT` - CREATE has no comment clause -; a change of the claims is `ALTER ROLE
 * r SET CLAIMS (…)` (the whole list - that is the form), of the comment `SET COMMENT`; dropped by `DROP ROLE r`.
 */
public class AclRole extends AclPlatformObject {
	public AclRole(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "role";
	}

	@Override
	@Property(viewable = true, editable = true, order = 1)
	public String getName() {
		return super.getName();
	}

	@Property(viewable = true, editable = true, updatable = true, order = 2, name = "Comment")
	public String getComment() {
		return edited("comment", text("comment"));
	}

	public void setComment(String comment) {
		edit("comment", comment);
	}

	/** The default claims as the list form writes them: `tenant = 'acme', unit = 'eu'`. */
	@Property(viewable = true, editable = true, updatable = true, order = 3, name = "Default claims",
	          description = "The role's default claims, as CLAIMS (…) writes them: name = 'value', …")
	public String getDefaultClaims(DBRProgressMonitor monitor) throws DBException {
		return editedOr("defaultClaims", () -> storedClaims(monitor));
	}

	public void setDefaultClaims(String claims) {
		edit("defaultClaims", claims);
	}

	private String storedClaims(DBRProgressMonitor monitor) throws DBException {
		List<String> pairs = new ArrayList<>();
		for (AclRoleClaim claim : getClaims(monitor)) {
			pairs.add(claim.getName() + " = " + AclGrammar.literal(claim.text("value")));
		}
		return String.join(", ", pairs);
	}

	@Override
	public boolean isEditable() {
		return true;
	}

	@Override
	protected String selfSql() {
		return PlatformSql.view("roles", "role", getName());
	}

	@Override
	protected String stored(DBRProgressMonitor monitor, String id) throws DBException {
		return switch (id) {
			case "comment" -> text("comment");
			case "defaultClaims" -> storedClaims(monitor);
			default -> null;
		};
	}

	@Override
	public List<String> createStatements() {
		String role = AclGrammar.ident(getName());
		List<String> out = new ArrayList<>();
		String claims = edited("defaultClaims", "");
		out.add("CREATE ROLE " + role + (AclGrammar.isBlank(claims) ? "" : " CLAIMS (" + claims.trim() + ")"));
		if (!AclGrammar.isBlank(getComment())) {
			out.add("ALTER ROLE " + role + " SET COMMENT " + AclGrammar.literal(getComment()));
		}
		return out;
	}

	@Override
	public List<String> alterStatements(DBRProgressMonitor monitor, Set<String> changed) throws DBException {
		String role = AclGrammar.ident(getName());
		List<String> out = new ArrayList<>();
		if (changed.contains("defaultClaims")) {
			String claims = getDefaultClaims(monitor);
			out.add("ALTER ROLE " + role + " SET CLAIMS " + (AclGrammar.isBlank(claims) ? "''" : "(" + claims.trim() + ")"));
		}
		if (changed.contains("comment")) {
			out.add("ALTER ROLE " + role + " SET COMMENT " + AclGrammar.literal(getComment()));
		}
		return out;
	}

	@Override
	public List<String> dropStatements() {
		return List.of("DROP ROLE " + AclGrammar.ident(getName()));
	}

	@Association
	public List<AclRoleClaim> getClaims(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "Claims", PlatformSql.view("role_claims", "role", getName()), r -> new AclRoleClaim(dataSource, this, r));
	}

	@Association
	public List<AclCatalogGrant> getCatalogGrants(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "CatalogGrants", PlatformSql.view("grants", "role", getName()), r -> new AclCatalogGrant(dataSource, this, r));
	}

	@Association
	public List<AclSchemaGrant> getSchemaGrants(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "SchemaGrants", PlatformSql.view("schema_grants", "role", getName()), r -> new AclSchemaGrant(dataSource, this, r));
	}

	@Association
	public List<AclObjectGrant> getObjectGrants(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "ObjectGrants", PlatformSql.view("object_grants", "role", getName()), r -> new AclObjectGrant(dataSource, this, r));
	}

	@Association
	public List<AclAdminScope> getAdminScopes(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "AdminScopes", PlatformSql.view("admins", "role", getName()), r -> new AclAdminScope(dataSource, this, r));
	}

	@Association
	public List<AclRoleGroup> getResourceGroups(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "ResourceGroups", PlatformSql.view("role_resource_groups", "role", getName()), r -> new AclRoleGroup(dataSource, this, r));
	}

	@Association
	public List<AclEffectiveCatalog> getEffectiveCatalogs(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "EffectiveCatalogs", PlatformSql.view("grants", "role", getName()), r -> new AclEffectiveCatalog(dataSource, this, r));
	}

	@Association
	public List<AclPlatformGrant> getPlatformGrants(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "PlatformGrants", PlatformSql.view("platform_grants", "role", getName()),
		                r -> new AclPlatformGrant(dataSource, this, r));
	}
}
