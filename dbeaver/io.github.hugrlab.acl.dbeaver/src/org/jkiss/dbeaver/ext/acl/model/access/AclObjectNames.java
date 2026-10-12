package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.List;

import org.jkiss.dbeaver.model.meta.IPropertyValueListProvider;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;

/**
 * The objects of a reference's catalog an end may name (spec 008.2): its tables, views and table functions (a To end
 * of kind function names one of the last). Read from the branch's own lists; a name may still be typed.
 */
public class AclObjectNames implements IPropertyValueListProvider<AclReferenceEntry> {
	@Override
	public boolean allowCustomValue() {
		return true;
	}

	@Override
	public Object[] getPossibleValues(AclReferenceEntry reference) {
		List<String> names = new ArrayList<>();
		if (!(reference.getParentObject() instanceof AclVirtualCatalog catalog)) {
			return names.toArray();
		}
		try {
			VoidProgressMonitor monitor = new VoidProgressMonitor();
			for (AclRelation relation : catalog.getRelations(monitor)) {
				names.add(relation.getName());
			}
			for (AclFunctionEntry function : catalog.getFunctionEntries(monitor)) {
				if (function.isTable()) {
					names.add(function.getName());
				}
			}
		} catch (Exception e) {
			// typed instead
		}
		return names.toArray();
	}
}
