package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.List;
import java.util.Map;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A role (platform.roles). One property per column of its view, the parent's key aside (spec 007.3). */
public class AclRole extends AclPlatformObject {
	public AclRole(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "role";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return super.getName();
	}

	@Property(viewable = true, order = 2, name = "Comment")
	public String getComment() {
		return text("comment");
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
