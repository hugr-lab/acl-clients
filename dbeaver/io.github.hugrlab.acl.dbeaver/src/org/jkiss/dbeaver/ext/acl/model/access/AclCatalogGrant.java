package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A role's catalog grant (platform.grants). One property per column of its view, the parent's key aside (spec 007.3). */
public class AclCatalogGrant extends AclPlatformObject {
	public AclCatalogGrant(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "catalog";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return super.getName();
	}

	@Property(viewable = true, order = 2, name = "Is main")
	public String getIsMain() {
		return text("is_main");
	}

	@Property(viewable = true, order = 3, name = "Caps")
	public String getCaps() {
		return PlatformValues.caps(row.get("caps"), "unstated: select, insert, update, delete, merge");
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
