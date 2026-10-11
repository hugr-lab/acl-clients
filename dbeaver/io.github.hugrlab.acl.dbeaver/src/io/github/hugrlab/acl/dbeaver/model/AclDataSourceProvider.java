package io.github.hugrlab.acl.dbeaver.model;

import org.jkiss.dbeaver.ext.generic.GenericDataSourceProvider;
import org.jkiss.dbeaver.ext.generic.model.GenericDataSource;

/** A duckdb-acl node: the Generic provider, with our meta model and dialect (spec 007). */
public class AclDataSourceProvider extends GenericDataSourceProvider<GenericDataSource> {
	public AclDataSourceProvider() {
		super(GenericDataSource.class);
	}
}
