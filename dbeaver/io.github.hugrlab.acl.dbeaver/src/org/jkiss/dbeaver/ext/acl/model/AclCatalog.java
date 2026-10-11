package org.jkiss.dbeaver.ext.acl.model;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.generic.model.GenericCatalog;
import org.jkiss.dbeaver.ext.generic.model.GenericDataSource;
import org.jkiss.dbeaver.ext.generic.model.GenericSchema;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

/** A virtual catalog: its schema list is the top level; a schema is found by its whole path at any depth. */
public class AclCatalog extends GenericCatalog {
	public AclCatalog(@NotNull GenericDataSource dataSource, @NotNull String catalogName) {
		super(dataSource, catalogName);
	}

	@Override
	public GenericSchema getSchema(DBRProgressMonitor monitor, String name) throws DBException {
		GenericSchema top = super.getSchema(monitor, name);
		if (top != null || name == null || getSchemas(monitor) == null) {
			return top;
		}
		for (GenericSchema schema : getSchemas(monitor)) {
			if (schema instanceof AclSchema acl) {
				AclSchema found = acl.findByPath(name);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}
}
