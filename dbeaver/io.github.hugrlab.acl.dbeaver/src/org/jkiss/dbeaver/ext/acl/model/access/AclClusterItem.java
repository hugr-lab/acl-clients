package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A cluster profile item (platform.cluster_items / cluster_effective, spec 093). Generated shape: one property per column (spec 007.3). */
public class AclClusterItem extends AclPlatformObject {
	public AclClusterItem(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "name";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return super.getName();
	}

	@Property(viewable = true, order = 2, name = "Kind")
	public String getKind() {
		return text("kind");
	}

	@Property(viewable = true, order = 3, name = "Scope")
	public String getScope() {
		return text("scope");
	}

	@Property(viewable = true, order = 4, name = "Class")
	public String getItemClass() {
		return text("class");
	}

	@Property(viewable = true, order = 5, name = "Version")
	public String getVersion() {
		return text("version");
	}

	@Property(viewable = true, order = 6, name = "Depends on")
	public String getDependsOn() {
		return text("depends_on");
	}

	@Property(viewable = true, order = 7, name = "Spec")
	public String getSpec() {
		return text("spec");
	}

	@Property(viewable = true, order = 8, name = "Comment")
	public String getComment() {
		return text("comment");
	}
}
