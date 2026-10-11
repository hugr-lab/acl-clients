package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A client of an issuer (platform.clients). Generated shape: one property per column (spec 007.3). */
public class AclClient extends AclPlatformObject {
	public AclClient(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
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

	@Property(viewable = true, order = 2, name = "Client id")
	public String getClientId() {
		return text("client_id");
	}

	@Property(viewable = true, order = 3, name = "Audiences")
	public String getAudiences() {
		return text("audiences");
	}

	@Property(viewable = true, order = 4, name = "Flows")
	public String getFlows() {
		return text("flows");
	}

	@Property(viewable = true, order = 5, name = "Unmapped")
	public String getUnmapped() {
		return text("unmapped");
	}

	@Property(viewable = true, order = 6, name = "Roles from")
	public String getRolesFrom() {
		return text("roles_from");
	}

	@Property(viewable = true, order = 7, name = "Implicit")
	public String getImplicit() {
		return text("implicit");
	}
}
