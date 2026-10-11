package org.jkiss.dbeaver.ext.acl.model;

import org.jkiss.dbeaver.ext.generic.model.GenericStructContainer;
import org.jkiss.dbeaver.ext.generic.model.GenericView;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;

/** A virtual view: its SQL name quotes each part of its schema's path (AclNames). */
public class AclView extends GenericView {
	public AclView(GenericStructContainer container, String tableName, String tableType, JDBCResultSet dbResult) {
		super(container, tableName, tableType, dbResult);
	}

	@Override
	public String getFullyQualifiedName(DBPEvaluationContext context) {
		return AclNames.qualified(getDataSource(), getCatalog(), getSchema(), getName());
	}
}
