package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A virtual function (platform.functions). Generated shape: one property per column (spec 007.3). */
public class AclFunctionEntry extends AclPlatformObject {
	public AclFunctionEntry(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
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

	@Property(viewable = true, order = 3, name = "Form")
	public String getForm() {
		return text("form");
	}

	@Property(viewable = true, order = 4, name = "Target")
	public String getTarget() {
		return text("target");
	}

	@Property(viewable = true, order = 5, name = "Params")
	public String getParams() {
		return text("params");
	}

	@Property(viewable = true, order = 6, name = "Comment")
	public String getComment() {
		return text("comment");
	}

	@Property(viewable = true, order = 7, name = "Created by")
	public String getCreatedBy() {
		return text("created_by");
	}
}
