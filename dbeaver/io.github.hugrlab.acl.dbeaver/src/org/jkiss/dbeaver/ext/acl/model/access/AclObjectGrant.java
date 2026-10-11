package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A role's object grant (platform.object_grants). One property per column of its view, the parent's key aside (spec 007.3). */
public class AclObjectGrant extends AclPlatformObject {
	public AclObjectGrant(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "object";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return text("catalog") + "." + text("object");
	}

	@Property(viewable = true, order = 2, name = "Catalog")
	public String getCatalog() {
		return text("catalog");
	}

	@Property(viewable = true, order = 3, name = "Caps")
	public String getCaps() {
		return PlatformValues.caps(row.get("caps"), "unstated: the catalog grant's");
	}

	@Property(viewable = true, order = 4, name = "Rls")
	public String getRls() {
		return text("rls");
	}

	@Property(viewable = true, order = 5, name = "Columns")
	public String getColumns() {
		return text("columns");
	}

	@Property(viewable = true, order = 50, name = "Rls checked")
	public String getRlsChecked() {
		return text("rls_checked");
	}
}
