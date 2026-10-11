package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.List;
import java.util.Map;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** An issuer (platform.issuers). Generated shape: one property per column (spec 007.3). */
public class AclIssuer extends AclPlatformObject {
	public AclIssuer(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
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

	@Property(viewable = true, order = 2, name = "Url")
	public String getUrl() {
		return text("url");
	}

	@Property(viewable = true, order = 3, name = "Secret service")
	public String getSecretService() {
		return text("secret_service");
	}

	@Property(viewable = true, order = 4, name = "Secret")
	public String getSecret() {
		return text("secret");
	}

	@Association
	public List<AclClient> getClients(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "Clients", PlatformSql.view("clients", "issuer", getName()), r -> new AclClient(dataSource, this, r));
	}
}
