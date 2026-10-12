package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.IPropertyValueListProvider;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * A role's administration bundle (platform.admins, duckdb-acl specs 009 / 117 / 118). Written (spec 008.2): `GRANT ADMIN
 * observe | operate | cluster | policy | manage | passthrough TO ROLE r`, `REVOKE ADMIN <bundle> FROM ROLE r` -
 * passthrough's alone (the node refuses anyone else). A catalog's `manage` is its catalog grant's caps, not a row
 * here to drop.
 */
public class AclAdminScope extends AclPlatformObject {
	public AclAdminScope(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "scope";
	}

	@Override
	@Property(viewable = true, editable = true, order = 1, listProvider = Bundles.class,
	          description = "observe, operate, cluster, policy, manage (= policy + observe), passthrough")
	public String getName() {
		String catalog = text("catalog");
		return catalog == null || catalog.isEmpty() ? super.getName() : super.getName() + " (" + catalog + ")";
	}

	@Property(viewable = true, order = 2, name = "Catalog")
	public String getCatalog() {
		return text("catalog");
	}

	private String role() {
		String role = text("role");
		return role == null && getParentObject() instanceof AclRole parent ? parent.getName() : role;
	}

	@Override
	public boolean isEditable() {
		return AclGrammar.isBlank(text("catalog")); // a catalog's manage is its catalog grant's
	}

	@Override
	public Set<String> functionsFor(Action action) {
		return switch (action) {
			case CREATE -> Set.of("grant_admin");
			case EDIT -> Set.of();
			case DELETE -> Set.of("revoke_admin");
		};
	}

	private String bundle() {
		return AclGrammar.ident(edited("name", text("scope")));
	}

	@Override
	public List<String> createStatements() {
		return List.of("GRANT ADMIN " + bundle() + " TO ROLE " + AclGrammar.ident(role()));
	}

	@Override
	public List<String> dropStatements() {
		return List.of("REVOKE ADMIN " + bundle() + " FROM ROLE " + AclGrammar.ident(role()));
	}

	public static class Bundles implements IPropertyValueListProvider<AclAdminScope> {
		@Override
		public boolean allowCustomValue() {
			return false;
		}

		@Override
		public Object[] getPossibleValues(AclAdminScope object) {
			return new Object[] {"observe", "operate", "cluster", "policy", "manage", "passthrough"};
		}
	}
}
