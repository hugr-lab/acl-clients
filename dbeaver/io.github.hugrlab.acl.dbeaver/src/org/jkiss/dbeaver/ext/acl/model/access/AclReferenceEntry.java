package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A declared reference (platform.references). One property per column of its view, the parent's key aside (spec 007.3). */
public class AclReferenceEntry extends AclPlatformObject {
	public AclReferenceEntry(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
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

	@Property(viewable = true, order = 2, name = "From object")
	public String getFromObject() {
		return text("from_object");
	}

	@Property(viewable = true, order = 3, name = "To object")
	public String getToObject() {
		return text("to_object");
	}

	@Property(viewable = true, order = 4, name = "To kind")
	public String getToKind() {
		return text("to_kind");
	}

	@Property(viewable = true, order = 5, name = "Cardinality")
	public String getCardinality() {
		return text("cardinality");
	}

	@Property(viewable = true, order = 6, name = "Optional")
	public String getOptional() {
		return text("optional");
	}

	@Property(viewable = true, order = 7, name = "Join method")
	public String getJoinMethod() {
		return text("join_method");
	}

	@Property(viewable = true, order = 8, name = "Comment")
	public String getComment() {
		return text("comment");
	}

	@Property(viewable = true, order = 50, name = "Expression")
	public String getExpression() {
		return text("expression");
	}
}
