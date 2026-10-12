package org.jkiss.dbeaver.ext.acl.model.edit;

import java.util.Map;

import org.jkiss.dbeaver.ext.acl.model.access.AclRole;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** Roles in the Access control branch (spec 008.1). */
public class AclRoleManager extends AclObjectManager<AclRole> {
	@Override
	protected AclRole make(DBSObject container) {
		return new AclRole(AclManagers.dataSource(container), container, Map.of());
	}
}
