package org.jkiss.dbeaver.ext.acl.model.access;

import org.jkiss.dbeaver.DBException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * A role's resource group (platform.role_resource_groups). Written (spec 008.2): `GRANT RESOURCE GROUP g TO ROLE r`,
 * `REVOKE RESOURCE GROUP g FROM ROLE r` - no edit (it is its key).
 */
public class AclRoleGroup extends AclPlatformObject {
	public AclRoleGroup(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "group";
	}

	@Override
	@Property(viewable = true, editable = true, order = 1, description = "The resource group")
	public String getName() {
		return super.getName();
	}

	private String role() {
		String role = text("role");
		return role == null && getParentObject() instanceof AclRole parent ? parent.getName() : role;
	}

	@Override
	public boolean isEditable() {
		return true;
	}

	@Override
	public Set<String> functionsFor(Action action) {
		return switch (action) {
			case CREATE -> Set.of("grant_resource_group");
			case EDIT -> Set.of();
			case DELETE -> Set.of("revoke_resource_group");
		};
	}

	@Override
	public List<String> createStatements() throws DBException {
		return List.of("GRANT RESOURCE GROUP " + AclGrammar.ident(getName()) + " TO ROLE " + AclGrammar.ident(role()));
	}

	@Override
	public List<String> dropStatements() {
		return List.of("REVOKE RESOURCE GROUP " + AclGrammar.ident(getName()) + " FROM ROLE " + AclGrammar.ident(role()));
	}
}
