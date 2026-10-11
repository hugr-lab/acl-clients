package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A declared column of a relation (platform.relation_columns). Generated shape: one property per column (spec 007.3). */
public class AclRelationColumn extends AclPlatformObject {
	public AclRelationColumn(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
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

	@Property(viewable = true, order = 2, name = "Pos")
	public String getPos() {
		return text("pos");
	}

	@Property(viewable = true, order = 3, name = "Expr")
	public String getExpr() {
		return text("expr");
	}

	@Property(viewable = true, order = 4, name = "Nullable")
	public String getNullable() {
		return text("nullable");
	}
}
