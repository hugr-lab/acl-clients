package org.jkiss.dbeaver.ext.acl.model;

import org.jkiss.dbeaver.ext.generic.GenericDataSourceProvider;
import org.jkiss.dbeaver.ext.generic.model.GenericDataSource;

/**
 * A duckdb-acl node: the Generic provider, with our meta model and dialect (acl-clients spec 007). The model lives
 * in DBeaver's `org.jkiss.dbeaver.ext.*` namespace on purpose: DBeaver evaluates the `visibleIf` of its editors and
 * trees with JEXL permitted only on `org.jkiss.dbeaver.*` classes, so a model class elsewhere hides them silently.
 */
public class AclDataSourceProvider extends GenericDataSourceProvider<GenericDataSource> {
	public AclDataSourceProvider() {
		super(GenericDataSource.class);
	}
}
