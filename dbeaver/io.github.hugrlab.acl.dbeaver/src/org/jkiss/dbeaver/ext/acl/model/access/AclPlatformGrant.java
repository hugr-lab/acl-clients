package org.jkiss.dbeaver.ext.acl.model.access;

import org.jkiss.dbeaver.DBException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.IPropertyValueListProvider;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * A role's point grant on `platform` or a source grant (platform.platform_grants, duckdb-acl specs 117 / 118.3).
 * Written (spec 008.2): `GRANT | DENY VIEW | FUNCTION platform.x TO ROLE r`, `REVOKE VIEW | FUNCTION platform.x FROM ROLE
 * r`; `GRANT SOURCE db[.schema] TO ROLE r` / `REVOKE SOURCE …`. Allowed changed (a view or a function) is the GRANT or
 * DENY again.
 */
public class AclPlatformGrant extends AclPlatformObject {
	public AclPlatformGrant(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "object";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		String object = getObject();
		if (object == null) {
			return "";
		}
		return "source".equals(getKind()) ? "source " + object : getKind() + " platform." + object;
	}

	@Property(viewable = true, editable = true, order = 2, name = "Kind", listProvider = Kinds.class,
	          description = "view, function (of platform) or source (a physical database or schema)")
	public String getKind() {
		return edited("kind", text("kind") == null ? "view" : text("kind"));
	}

	public void setKind(String value) {
		edit("kind", value);
	}

	@Property(viewable = true, editable = true, order = 3, name = "Object",
	          description = "A platform view or function (sessions, kill_session), or a source: db or db.schema")
	public String getObject() {
		return edited("object", text("object"));
	}

	public void setObject(String value) {
		edit("object", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 4, name = "Allowed",
	          description = "Grant (ticked) or deny - a deny wins over every bundle but passthrough")
	public boolean isAllowed() {
		return edited("allowed", row.get("allowed") == null || Boolean.TRUE.equals(row.get("allowed")));
	}

	public void setAllowed(boolean value) {
		edit("allowed", value);
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
		boolean source = "source".equals(getKind());
		return switch (action) {
			case CREATE, EDIT -> Set.of(source ? "grant_source" : "grant_platform");
			case DELETE -> Set.of(source ? "revoke_source" : "revoke_platform");
		};
	}

	private String subject() {
		if ("source".equals(getKind())) {
			return "SOURCE " + AclGrammar.path(getObject().trim());
		}
		return ("function".equals(getKind()) ? "FUNCTION" : "VIEW") + " platform." + AclGrammar.ident(getObject().trim());
	}

	@Override
	public List<String> createStatements() throws DBException {
		if ("source".equals(getKind())) {
			return List.of("GRANT " + subject() + " TO ROLE " + AclGrammar.ident(role()));
		}
		return List.of((isAllowed() ? "GRANT " : "DENY ") + subject() + " TO ROLE " + AclGrammar.ident(role()));
	}

	@Override
	public List<String> alterStatements(DBRProgressMonitor monitor, Set<String> changed) throws DBException {
		return changed.contains("allowed") && !"source".equals(getKind()) ? createStatements() : List.of();
	}

	@Override
	public List<String> dropStatements() {
		return List.of("REVOKE " + subject() + " FROM ROLE " + AclGrammar.ident(role()));
	}

	public static class Kinds implements IPropertyValueListProvider<AclPlatformGrant> {
		@Override
		public boolean allowCustomValue() {
			return false;
		}

		@Override
		public Object[] getPossibleValues(AclPlatformGrant object) {
			return new Object[] {"view", "function", "source"};
		}
	}
}
