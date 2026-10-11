package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.List;
import java.util.Map;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A function category (platform.function_categories, spec 072). One property per column of its view, the parent's key aside (spec 007.3). */
public class AclFunctionCategory extends AclPlatformObject {
	public AclFunctionCategory(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "category";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return super.getName();
	}

	@Property(viewable = true, order = 2, name = "Comment")
	public String getComment() {
		return text("comment");
	}

	@Property(viewable = true, order = 3, name = "Builtin")
	public String getBuiltin() {
		return text("builtin");
	}

	@Association
	public List<AclCategoryMember> getMembers(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "Members", PlatformSql.view("function_category_members", "category", getName()), r -> new AclCategoryMember(dataSource, this, r));
	}
}
