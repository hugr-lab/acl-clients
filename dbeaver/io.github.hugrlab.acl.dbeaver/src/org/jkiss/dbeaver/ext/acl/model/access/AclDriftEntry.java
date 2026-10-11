package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** Where the node differs from its profile (platform.drift, spec 118.2). One property per column of its view, the parent's key aside (spec 007.3). */
public class AclDriftEntry extends AclPlatformObject {
	public AclDriftEntry(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "name";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return super.getName();
	}

	@Property(viewable = true, order = 2, name = "Kind")
	public String getKind() {
		return text("kind");
	}

	@Property(viewable = true, order = 3, name = "State")
	public String getState() {
		return text("state");
	}

	@Property(viewable = true, order = 4, name = "Node value")
	public String getNodeValue() {
		return text("node_value");
	}

	@Property(viewable = true, order = 5, name = "Profile value")
	public String getProfileValue() {
		return text("profile_value");
	}

	@Property(viewable = true, order = 6, name = "Scope")
	public String getScope() {
		return text("scope");
	}
}
