package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.List;
import java.util.Map;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A catalog a role holds a grant on - its effective rights there (platform.effective_rights). One property per column of its view, the parent's key aside (spec 007.3). */
public class AclEffectiveCatalog extends AclPlatformObject {
	public AclEffectiveCatalog(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "catalog";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return super.getName();
	}

	@Association
	public List<AclEffectiveRight> getRights(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "Rights", PlatformSql.call("effective_rights", text("role"), getName()), r -> new AclEffectiveRight(dataSource, this, r));
	}
}
