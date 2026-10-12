package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.List;

import org.jkiss.dbeaver.model.meta.IPropertyValueListProvider;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;

/** The virtual catalogs a new grant may name: the branch's own list (read once, as its folder reads it). */
public class AclCatalogNames implements IPropertyValueListProvider<AclPlatformObject> {
	@Override
	public boolean allowCustomValue() {
		return true; // a catalog the listing does not show yet (made in another session) - the node decides
	}

	@Override
	public Object[] getPossibleValues(AclPlatformObject object) {
		List<String> names = new ArrayList<>();
		try {
			for (AclVirtualCatalog catalog : object.getDataSource().getAccessCatalogs(new VoidProgressMonitor())) {
				names.add(catalog.getName());
			}
		} catch (Exception e) {
			// no list: the name is typed
		}
		return names.toArray();
	}
}
