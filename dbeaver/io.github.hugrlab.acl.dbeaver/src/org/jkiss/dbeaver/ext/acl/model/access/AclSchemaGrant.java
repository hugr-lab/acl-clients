package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A role's schema grant (platform.schema_grants). Generated shape: one property per column (spec 007.3). */
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
		return super.getName();
	}

	@Property(viewable = true, order = 2, name = "Catalog")
	public String getCatalog() {
		return text("catalog");
	}

	@Property(viewable = true, order = 3, name = "Caps")
	public String getCaps() {
		return text("caps");
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
}
