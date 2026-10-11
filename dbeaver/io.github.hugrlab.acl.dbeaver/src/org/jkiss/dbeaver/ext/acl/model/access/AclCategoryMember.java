package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A function in a category (platform.function_category_members). One property per column of its view, the parent's key aside (spec 007.3). */
public class AclCategoryMember extends AclPlatformObject {
	public AclCategoryMember(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "name";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return text("database") + "." + text("schema") + "." + text("name") + " [" + text("kind") + "]";
	}

	@Property(viewable = true, order = 2, name = "Database")
	public String getDatabase() {
		return text("database");
	}

	@Property(viewable = true, order = 3, name = "Schema")
	public String getSchema() {
		return text("schema");
	}

	@Property(viewable = true, order = 4, name = "Kind")
	public String getKind() {
		return text("kind");
	}
}
