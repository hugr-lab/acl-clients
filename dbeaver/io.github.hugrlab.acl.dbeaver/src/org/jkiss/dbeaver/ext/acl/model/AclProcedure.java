package org.jkiss.dbeaver.ext.acl.model;

import org.jkiss.dbeaver.ext.generic.model.GenericFunctionResultType;
import org.jkiss.dbeaver.ext.generic.model.GenericProcedure;
import org.jkiss.dbeaver.ext.generic.model.GenericStructContainer;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.struct.rdb.DBSProcedureType;

/** A virtual function: its SQL name quotes each part of its schema's path (AclNames). */
public class AclProcedure extends GenericProcedure {
	public AclProcedure(GenericStructContainer container, String procedureName, String specificName, String remarks,
	                    DBSProcedureType procedureType, GenericFunctionResultType functionResultType) {
		super(container, procedureName, specificName, remarks, procedureType, functionResultType);
	}

	@Override
	public String getFullyQualifiedName(DBPEvaluationContext context) {
		return AclNames.qualified(getDataSource(), getCatalog(), getSchema(), getName());
	}
}
