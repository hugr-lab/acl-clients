package org.jkiss.dbeaver.ext.acl.model;

import java.util.ArrayList;
import java.util.List;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.generic.model.GenericCatalog;
import org.jkiss.dbeaver.ext.generic.model.GenericDataSource;
import org.jkiss.dbeaver.ext.generic.model.GenericSchema;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBPOverloadedObject;
import org.jkiss.dbeaver.model.DBPQualifiedObject;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * A virtual schema: its name is the whole path the node lists (`raw.eu` - what every listing filters by); under its
 * parent it shows the last part, its parent object is that schema, and it is found by its path (spec 007.1).
 */
public class AclSchema extends GenericSchema implements DBPOverloadedObject, DBPQualifiedObject {
	private final List<AclSchema> children = new ArrayList<>();
	private AclSchema parent;

	public AclSchema(@NotNull GenericDataSource dataSource, @Nullable GenericCatalog catalog, @NotNull String path) {
		super(dataSource, catalog, path);
	}

	/** The schemas nested in this one (the tree's recursive item). */
	@Association
	public List<AclSchema> getChildSchemas() {
		return children;
	}

	void addChild(AclSchema child) {
		children.add(child);
		child.parent = this;
	}

	@Nullable
	AclSchema findByPath(String path) {
		if (getName().equals(path)) {
			return this;
		}
		for (AclSchema child : children) {
			AclSchema found = child.findByPath(path);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	@Override
	public DBSObject getParentObject() {
		return parent != null ? parent : super.getParentObject();
	}

	@Override
	public DBSObject getChild(DBRProgressMonitor monitor, String childName) throws DBException {
		for (AclSchema child : children) {
			if (SchemaPath.leaf(child.getName()).equals(childName) || child.getName().equals(childName)) {
				return child;
			}
		}
		return super.getChild(monitor, childName);
	}

	@NotNull
	@Override
	public String getFullyQualifiedName(DBPEvaluationContext context) {
		return AclNames.qualified(getDataSource(), getCatalog(), this, null);
	}

	/** The last part under a parent; the whole path where the tree shows it on top (its parent is not listed). */
	@NotNull
	@Override
	public String getOverloadedName() {
		return parent != null ? SchemaPath.leaf(getName()) : getName();
	}
}
