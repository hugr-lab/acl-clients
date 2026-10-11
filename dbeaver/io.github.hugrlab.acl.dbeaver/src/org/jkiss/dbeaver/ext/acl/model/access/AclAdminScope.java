package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** An administration scope a role holds (platform.admins). One property per column of its view, the parent's key aside (spec 007.3). */
public class AclAdminScope extends AclPlatformObject {
	public AclAdminScope(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "scope";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return text("catalog") == null || text("catalog").isEmpty() ? text("scope")
		                                                             : text("scope") + " (" + text("catalog") + ")";
	}

	@Property(viewable = true, order = 2, name = "Catalog")
	public String getCatalog() {
		return text("catalog");
	}
}
