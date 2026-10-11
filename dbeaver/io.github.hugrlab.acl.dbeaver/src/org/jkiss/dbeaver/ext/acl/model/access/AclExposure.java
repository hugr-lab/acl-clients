package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A role's exposure to a physical object (platform.exposures, spec 120.2). One property per column of its view, the parent's key aside (spec 007.3). */
public class AclExposure extends AclPlatformObject {
	public AclExposure(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "object";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		String object = text("object");
		return (object == null ? "(catalog grant)" : object) + " (" + text("catalog") + ") - " + text("role");
	}

	@Property(viewable = true, order = 2, name = "Source")
	public String getSource() {
		return text("source");
	}

	@Property(viewable = true, order = 3, name = "Catalog")
	public String getCatalog() {
		return text("catalog");
	}

	@Property(viewable = true, order = 4, name = "Kind")
	public String getKind() {
		return text("kind");
	}

	@Property(viewable = true, order = 5, name = "Role")
	public String getRole() {
		return text("role");
	}

	@Property(viewable = true, order = 6, name = "Decided by")
	public String getDecidedBy() {
		return text("decided_by");
	}

	@Property(viewable = true, order = 7, name = "Caps")
	public String getCaps() {
		return text("caps");
	}
}
