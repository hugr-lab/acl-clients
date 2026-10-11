package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A virtual schema (platform.schemas). One property per column of its view, the parent's key aside (spec 007.3). */
public class AclVirtualSchema extends AclPlatformObject {
	public AclVirtualSchema(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "path";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return super.getName();
	}

	@Property(viewable = true, order = 2, name = "Phys path")
	public String getPhysPath() {
		return text("phys_path");
	}

	@Property(viewable = true, order = 3, name = "Origin")
	public String getOrigin() {
		return text("origin");
	}

	@Property(viewable = true, order = 4, name = "Comment")
	public String getComment() {
		return text("comment");
	}

	@Property(viewable = true, order = 5, name = "Created by")
	public String getCreatedBy() {
		return text("created_by");
	}
}
