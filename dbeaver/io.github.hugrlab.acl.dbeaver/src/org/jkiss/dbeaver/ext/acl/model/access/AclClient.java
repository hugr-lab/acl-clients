package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A client of an issuer (platform.clients). One property per column of its view, the parent's key aside (spec 007.3). */
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

	@Property(viewable = true, order = 50, name = "Azp")
	public String getAzp() {
		return text("azp");
	}

	@Property(viewable = true, order = 51, name = "Requires")
	public String getRequires() {
		return text("requires");
	}

	@Property(viewable = true, order = 52, name = "Roles constant")
	public String getRolesConstant() {
		return text("roles_constant");
	}

	@Property(viewable = true, order = 53, name = "Attributes")
	public String getAttributes() {
		return text("attributes");
	}

	@Property(viewable = true, order = 54, name = "Subject")
	public String getSubject() {
		return text("subject");
	}

	@Property(viewable = true, order = 55, name = "Token type")
	public String getTokenType() {
		return text("token_type");
	}

	@Property(viewable = true, order = 56, name = "Secret service")
	public String getSecretService() {
		return text("secret_service");
	}

	@Property(viewable = true, order = 57, name = "Secret")
	public String getSecret() {
		return text("secret");
	}
}
