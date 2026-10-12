package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * A function grant or deny (platform.function_grants, duckdb-acl spec 072): a category or one function, to a role or to
 * every role. Written (spec 008.2): `GRANT | DENY FUNCTION [CATEGORY] x TO ROLE r | TO ALL ROLES`, `REVOKE FUNCTION
 * [CATEGORY] x FROM ROLE r | FROM ALL ROLES`; Allowed changed is the GRANT or DENY again (it replaces the row).
 */
public class AclFunctionGrant extends AclPlatformObject {
	public AclFunctionGrant(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "role";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		String role = getRole();
		String what = !AclGrammar.isBlank(getCategory()) ? "category " + getCategory() : getFunction();
		return (role == null || role.isEmpty() ? "(every role)" : role) + " - " + (what == null ? "" : what);
	}

	@Property(viewable = true, editable = true, order = 2, name = "Role", description = "Empty = every role")
	public String getRole() {
		return edited("role", text("role"));
	}

	public void setRole(String value) {
		edit("role", value);
	}

	@Property(viewable = true, editable = true, order = 3, name = "Category", description = "A category - or a function below")
	public String getCategory() {
		return edited("category", text("category"));
	}

	public void setCategory(String value) {
		edit("category", value);
	}

	@Property(viewable = true, editable = true, order = 4, name = "Function",
	          description = "One function: lower, system.main.upper, range TABLE")
	public String getFunction() {
		if (text("name") == null) {
			return edited("function", "");
		}
		return AclGrammar.ident(text("database")) + "." + AclGrammar.ident(text("schema")) + "." + AclGrammar.ident(text("name"))
		       + ("table".equals(text("kind")) ? " TABLE" : "");
	}

	public void setFunction(String value) {
		edit("function", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 5, name = "Allowed",
	          description = "Grant (ticked) or deny - a deny anywhere wins")
	public boolean isAllowed() {
		return edited("allowed", row.get("allowed") == null || Boolean.TRUE.equals(row.get("allowed")));
	}

	public void setAllowed(boolean value) {
		edit("allowed", value);
	}

	@Override
	public boolean isEditable() {
		return true;
	}

	@Override
	public Set<String> functionsFor(Action action) {
		boolean category = !AclGrammar.isBlank(getCategory());
		return switch (action) {
			case CREATE, EDIT -> Set.of(category ? "grant_function_category" : "grant_function");
			case DELETE -> Set.of(category ? "revoke_function_category" : "revoke_function");
		};
	}

	private String subject() {
		return AclGrammar.isBlank(getCategory()) ? "FUNCTION " + getFunction().trim()
		                                         : "FUNCTION CATEGORY " + AclGrammar.ident(getCategory().trim());
	}

	private String who(String preposition) {
		return AclGrammar.isBlank(getRole()) ? preposition + " ALL ROLES" : preposition + " ROLE " + AclGrammar.ident(getRole());
	}

	@Override
	public List<String> createStatements() {
		return List.of((isAllowed() ? "GRANT " : "DENY ") + subject() + " " + who("TO"));
	}

	@Override
	public List<String> alterStatements(DBRProgressMonitor monitor, Set<String> changed) {
		return changed.contains("allowed") ? createStatements() : List.of();
	}

	@Override
	public List<String> dropStatements() {
		return List.of("REVOKE " + subject() + " " + who("FROM"));
	}
}
