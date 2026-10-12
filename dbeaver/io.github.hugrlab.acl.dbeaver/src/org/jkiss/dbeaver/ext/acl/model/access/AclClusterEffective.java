package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;

import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** What of the profile applies to this node (platform.cluster_effective): read only - the profile's items are edited. */
public class AclClusterEffective extends AclClusterItem {
	public AclClusterEffective(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	public boolean isEditable() {
		return false;
	}
}
