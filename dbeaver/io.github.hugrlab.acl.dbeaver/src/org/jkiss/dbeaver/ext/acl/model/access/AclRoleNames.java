package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.List;

import org.jkiss.dbeaver.model.meta.IPropertyValueListProvider;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;

/** The roles a field may name: the branch's own list (read once, as its folder reads it). */
public class AclRoleNames implements IPropertyValueListProvider<AclPlatformObject> {
	@Override
	public boolean allowCustomValue() {
		return true;
	}

	@Override
	public Object[] getPossibleValues(AclPlatformObject object) {
		List<String> names = new ArrayList<>();
		try {
			for (AclRole role : object.getDataSource().getAccessRoles(new VoidProgressMonitor())) {
				names.add(role.getName());
			}
		} catch (Exception e) {
			// typed instead
		}
		return names.toArray();
	}
}
