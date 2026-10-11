package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;

import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A point grant or deny on one view or function of the platform catalog (platform.platform_grants, spec 117). */
public class AclPlatformGrant extends AclPlatformObject {
	public AclPlatformGrant(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "object";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return text("kind") + " platform." + text("object");
	}

	@Property(viewable = true, order = 2, name = "Allowed")
	public String getAllowed() {
		return text("allowed");
	}
}
