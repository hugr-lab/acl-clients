package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * A role's schema grant (platform.schema_grants). Read: one property per column of its view (spec 007.3). Written (spec
 * 008.1): `GRANT SCHEMA c.path TO ROLE r [WITH (…) | CAPS '{}'] [INTO <phys schema> | VIRTUAL ONLY] [COMMENT '…']`. The
 * grammar has no ALTER for it and a GRANT replaces it whole (verified on the node), so a change writes the grant again
 * with every property the form shows - which are all of its facts; `REVOKE SCHEMA c.path FROM ROLE r`. An inherited row
 * (capabilities materialised from an ancestor) states none of its own: unstated.
 */
public class AclSchemaGrant extends AclGrantObject {
	public AclSchemaGrant(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "schema_path";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		String catalog = getCatalog();
		String path = getSchemaPath();
		return catalog == null || path == null ? "" : catalog + "." + path;
	}

	@Property(viewable = true, editable = true, order = 2, name = "Catalog", listProvider = AclCatalogNames.class)
	public String getCatalog() {
		return edited("catalog", text("catalog"));
	}

	public void setCatalog(String catalog) {
		edit("catalog", catalog);
	}

	@Property(viewable = true, editable = true, order = 3, name = "Schema path",
	          description = "The schema inside the catalog, as its path: raw or raw.eu")
	public String getSchemaPath() {
		return edited("schemaPath", text("schema_path"));
	}

	public void setSchemaPath(String path) {
		edit("schemaPath", path);
	}

	@Override
	protected Set<String> storedCaps() {
		return Boolean.TRUE.equals(row.get("inherited")) ? null : super.storedCaps();
	}

	@Property(viewable = true, order = 4, name = "Caps")
	public String getCaps() {
		Set<String> caps = caps();
		return caps == null ? "unstated: the catalog grant's" : caps.isEmpty() ? "none" : capsText(caps);
	}

	@Property(viewable = true, order = 5, name = "Inherited")
	public String getInherited() {
		return text("inherited");
	}

	@Property(viewable = true, editable = true, updatable = true, order = 6, name = "Virtual only",
	          description = "The role only registers objects that already exist physically")
	public boolean isVirtualOnly() {
		return edited("virtualOnly", Boolean.TRUE.equals(row.get("virtual_only")));
	}

	public void setVirtualOnly(boolean virtualOnly) {
		edit("virtualOnly", virtualOnly);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 7, name = "Comment")
	public String getComment() {
		return edited("comment", text("comment"));
	}

	public void setComment(String comment) {
		edit("comment", comment);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 50, name = "Into",
	          description = "The physical schema a role's CREATE lands in (db.schema)")
	public String getInto() {
		return edited("into", text("into"));
	}

	public void setInto(String into) {
		edit("into", into);
	}

	@Override
	public boolean isEditable() {
		return true;
	}

	@Override
	protected String selfSql() {
		return PlatformSql.view("schema_grants", "role", role(), "catalog", getCatalog(), "schema_path", getSchemaPath());
	}

	@Override
	protected String stored(DBRProgressMonitor monitor, String id) {
		if (isCapsProperty(id)) {
			return capsText(storedCaps());
		}
		return switch (id) {
			case "virtualOnly" -> String.valueOf(Boolean.TRUE.equals(row.get("virtual_only")));
			case "comment" -> text("comment");
			case "into" -> text("into");
			default -> null;
		};
	}

	private String grant() {
		StringBuilder sql = new StringBuilder("GRANT SCHEMA ").append(AclGrammar.inCatalog(getCatalog(), getSchemaPath()))
		    .append(" TO ROLE ").append(AclGrammar.ident(role())).append(AclGrammar.grantCaps(caps()));
		if (!AclGrammar.isBlank(getInto())) {
			sql.append(" INTO ").append(AclGrammar.path(getInto().trim()));
		} else if (isVirtualOnly()) {
			sql.append(" VIRTUAL ONLY");
		}
		if (!AclGrammar.isBlank(getComment())) {
			sql.append(" COMMENT ").append(AclGrammar.literal(getComment()));
		}
		return sql.toString();
	}

	@Override
	public List<String> createStatements() {
		return List.of(grant());
	}

	@Override
	public List<String> alterStatements(DBRProgressMonitor monitor, Set<String> changed) {
		return changed.isEmpty() ? List.of() : List.of(grant());
	}

	@Override
	public List<String> dropStatements() {
		return List.of("REVOKE SCHEMA " + AclGrammar.inCatalog(getCatalog(), getSchemaPath()) + " FROM ROLE "
		               + AclGrammar.ident(role()));
	}
}
