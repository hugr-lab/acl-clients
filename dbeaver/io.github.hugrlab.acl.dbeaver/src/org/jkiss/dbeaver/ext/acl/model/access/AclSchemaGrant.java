package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A role's schema grant (platform.schema_grants). One property per column of its view, the parent's key aside (spec 007.3). */
public class AclSchemaGrant extends AclPlatformObject {
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
		return text("catalog") + "." + text("schema_path");
	}

	@Property(viewable = true, order = 2, name = "Catalog")
	public String getCatalog() {
		return text("catalog");
	}

	@Property(viewable = true, order = 3, name = "Caps")
	public String getCaps() {
		return PlatformValues.caps(row.get("caps"), "unstated: the catalog grant's");
	}

	@Property(viewable = true, order = 4, name = "Inherited")
	public String getInherited() {
		return text("inherited");
	}

	@Property(viewable = true, order = 5, name = "Virtual only")
	public String getVirtualOnly() {
		return text("virtual_only");
	}

	@Property(viewable = true, order = 6, name = "Comment")
	public String getComment() {
		return text("comment");
	}

	@Property(viewable = true, order = 50, name = "Into")
	public String getInto() {
		return text("into");
	}
}
