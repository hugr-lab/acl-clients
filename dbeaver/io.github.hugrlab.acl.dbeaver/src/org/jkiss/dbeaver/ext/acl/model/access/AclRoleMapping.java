package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.IPropertyValueListProvider;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * A role mapping (platform.role_mappings, duckdb-acl spec 095): a value at a client's ROLES FROM paths becomes a role.
 * Written (spec 008.2): `MAP GROUP | CLAIM '<value>' FROM CLIENT | ISSUER <n> TO ROLE <r>`, `DROP MAP …` - a mapping is
 * its key, so there is no edit (drop and map again). A role holding administration is mapped FROM CLIENT only (the
 * node's rule).
 */
public class AclRoleMapping extends AclPlatformObject {
	public AclRoleMapping(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "external_value";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		String value = getExternalValue();
		return value == null ? "" : value + " → " + getRole() + " (" + getScopeKind() + " " + getScopeName() + ")";
	}

	@Property(viewable = true, editable = true, order = 2, name = "Source", listProvider = Sources.class,
	          description = "claim-value (a plain claim value) or group (a group identifier, an EntraID GUID)")
	public String getSource() {
		return edited("source", text("source") == null ? "claim-value" : text("source"));
	}

	public void setSource(String value) {
		edit("source", value);
	}

	@Property(viewable = true, editable = true, order = 3, name = "External value")
	public String getExternalValue() {
		return edited("externalValue", text("external_value"));
	}

	public void setExternalValue(String value) {
		edit("externalValue", value);
	}

	@Property(viewable = true, editable = true, order = 4, name = "Scope kind", listProvider = Scopes.class,
	          description = "client (one client) or issuer (every client of one issuer)")
	public String getScopeKind() {
		return edited("scopeKind", text("scope_kind") == null ? "client" : text("scope_kind"));
	}

	public void setScopeKind(String value) {
		edit("scopeKind", value);
	}

	@Property(viewable = true, editable = true, order = 5, name = "Scope name", description = "The client's or issuer's name")
	public String getScopeName() {
		return edited("scopeName", text("scope_name"));
	}

	public void setScopeName(String value) {
		edit("scopeName", value);
	}

	@Property(viewable = true, editable = true, order = 6, name = "Role")
	public String getRole() {
		return edited("role", text("role"));
	}

	public void setRole(String value) {
		edit("role", value);
	}

	@Override
	public boolean isEditable() {
		return true;
	}

	@Override
	public Set<String> functionsFor(Action action) {
		return switch (action) {
			case CREATE -> Set.of("map_role");
			case EDIT -> Set.of();
			case DELETE -> Set.of("drop_role_mapping");
		};
	}

	private String mapping() {
		return ("group".equals(getSource()) ? "GROUP " : "CLAIM ") + AclGrammar.literal(getExternalValue()) + " FROM "
		       + ("issuer".equals(getScopeKind()) ? "ISSUER " : "CLIENT ") + AclGrammar.ident(getScopeName()) + " TO ROLE "
		       + AclGrammar.ident(getRole());
	}

	@Override
	public List<String> createStatements() {
		return List.of("MAP " + mapping());
	}

	@Override
	public List<String> dropStatements() {
		return List.of("DROP MAP " + mapping());
	}

	public static class Sources implements IPropertyValueListProvider<AclRoleMapping> {
		@Override
		public boolean allowCustomValue() {
			return false;
		}

		@Override
		public Object[] getPossibleValues(AclRoleMapping object) {
			return new Object[] {"claim-value", "group"};
		}
	}

	public static class Scopes implements IPropertyValueListProvider<AclRoleMapping> {
		@Override
		public boolean allowCustomValue() {
			return false;
		}

		@Override
		public Object[] getPossibleValues(AclRoleMapping object) {
			return new Object[] {"client", "issuer"};
		}
	}
}
