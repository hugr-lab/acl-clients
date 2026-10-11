package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.List;
import java.util.Map;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A physical source the policy builds over (the first part of a relation's or a schema's physical name). */
public class AclSource extends AclPlatformObject {
	public AclSource(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "source";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return super.getName();
	}

	/** Who reads what of it, and why (platform.exposures, spec 120.2). */
	@Association
	public List<AclExposure> getExposures(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "Exposures", PlatformSql.call("exposures", getName()),
		                r -> new AclExposure(dataSource, this, r));
	}
}
