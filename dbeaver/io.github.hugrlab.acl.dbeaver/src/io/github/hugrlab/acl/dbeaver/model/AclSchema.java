package io.github.hugrlab.acl.dbeaver.model;

import java.util.ArrayList;
import java.util.List;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.ext.generic.model.GenericCatalog;
import org.jkiss.dbeaver.ext.generic.model.GenericDataSource;
import org.jkiss.dbeaver.ext.generic.model.GenericSchema;
import org.jkiss.dbeaver.model.DBPOverloadedObject;
import org.jkiss.dbeaver.model.meta.Association;

/**
 * A virtual schema: its name is the whole path the node lists (`raw.eu` - what every listing filters by); the tree
 * shows the last part under its parent schema (spec 007.1).
 */
public class AclSchema extends GenericSchema implements DBPOverloadedObject {
	private final List<AclSchema> children = new ArrayList<>();

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
	}

	@NotNull
	@Override
	public String getOverloadedName() {
		return SchemaPath.leaf(getName());
	}
}
