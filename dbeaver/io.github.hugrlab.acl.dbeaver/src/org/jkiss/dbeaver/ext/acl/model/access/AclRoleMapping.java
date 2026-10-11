package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A role mapping (platform.role_mappings). Generated shape: one property per column (spec 007.3). */
public class AclRoleMapping extends AclPlatformObject {
	public AclRoleMapping(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "external_value";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return super.getName();
	}

	@Property(viewable = true, order = 2, name = "Role")
	public String getRole() {
		return text("role");
	}

	@Property(viewable = true, order = 3, name = "Scope kind")
	public String getScopeKind() {
		return text("scope_kind");
	}

	@Property(viewable = true, order = 4, name = "Scope name")
	public String getScopeName() {
		return text("scope_name");
	}

	@Property(viewable = true, order = 5, name = "Source")
	public String getSource() {
		return text("source");
	}
}
