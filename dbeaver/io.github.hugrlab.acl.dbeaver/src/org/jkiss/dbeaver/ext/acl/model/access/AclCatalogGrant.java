package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * A role's catalog grant (platform.grants). Read: one property per column of its view (spec 007.3). Written (spec
 * 008.1): `GRANT CATALOG c TO ROLE r [WITH (…) | CAPS '{}'] [MAIN] [RLS (…)] [COLUMNS (…)]`; a change is one `ALTER
 * GRANT CATALOG c TO ROLE r SET CAPS | MAIN | RLS | COLUMNS` per property; `REVOKE CATALOG c FROM ROLE r` (which takes
 * the role's object grants in that catalog with it - the node's rule).
 */
public class AclCatalogGrant extends AclGrantObject {
	public AclCatalogGrant(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "catalog";
	}

	@Override
	@Property(viewable = true, editable = true, order = 1, listProvider = AclCatalogNames.class,
	          description = "The virtual catalog granted")
	public String getName() {
		return super.getName();
	}

	@Property(viewable = true, editable = true, updatable = true, order = 2, name = "Is main",
	          description = "The catalog whose objects the role addresses unqualified")
	public boolean isMain() {
		return edited("main", Boolean.TRUE.equals(row.get("is_main")));
	}

	public void setMain(boolean main) {
		edit("main", main);
	}

	@Property(viewable = true, order = 3, name = "Caps")
	public String getCaps() {
		Set<String> caps = caps();
		return caps == null ? "unstated: select, insert, update, delete, merge" : caps.isEmpty() ? "none" : capsText(caps);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 4, name = "Rls",
	          description = "The grant's own predicate over the physical columns, AND-ed onto every object of the catalog")
	public String getRls() {
		return edited("rls", text("rls"));
	}

	public void setRls(String rls) {
		edit("rls", rls);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 5, name = "Columns",
	          description = "The grant's own column list, as COLUMNS (…) writes it: id, amount, ssn = NULL, address.city")
	public String getColumns() {
		return edited("columns", text("columns"));
	}

	public void setColumns(String columns) {
		edit("columns", columns);
	}

	@Property(viewable = true, order = 50, name = "Rls checked")
	public String getRlsChecked() {
		return text("rls_checked");
	}

	@Override
	public boolean isEditable() {
		return true;
	}

	@Override
	public Set<String> functionsFor(Action action) {
		return switch (action) {
			case CREATE -> Set.of("grant_catalog");
			case EDIT -> Set.of("alter_grant");
			case DELETE -> Set.of("revoke_catalog");
		};
	}

	@Override
	public String catalogOf() {
		return getName();
	}

	@Override
	protected String selfSql() {
		return PlatformSql.view("grants", "role", role(), "catalog", getName());
	}

	@Override
	protected String stored(DBRProgressMonitor monitor, String id) {
		if (isCapsProperty(id)) {
			return capsText(storedCaps());
		}
		return switch (id) {
			case "main" -> String.valueOf(Boolean.TRUE.equals(row.get("is_main")));
			case "rls" -> text("rls");
			case "columns" -> text("columns");
			default -> null;
		};
	}

	private String target() {
		return AclGrammar.path(getName()) + " TO ROLE " + AclGrammar.ident(role());
	}

	@Override
	public List<String> createStatements() {
		StringBuilder sql = new StringBuilder("GRANT CATALOG ").append(target()).append(AclGrammar.grantCaps(caps()));
		if (isMain()) {
			sql.append(" MAIN");
		}
		if (!AclGrammar.isBlank(getRls())) {
			sql.append(" RLS (").append(getRls().trim()).append(')');
		}
		if (!AclGrammar.isBlank(getColumns())) {
			sql.append(" COLUMNS (").append(getColumns().trim()).append(')');
		}
		return List.of(sql.toString());
	}

	@Override
	public List<String> alterStatements(DBRProgressMonitor monitor, Set<String> changed) {
		String alter = "ALTER GRANT CATALOG " + target() + " SET ";
		List<String> out = new ArrayList<>();
		if (anyCaps(changed)) {
			out.add(alter + "CAPS " + AclGrammar.capsJson(caps()));
		}
		if (changed.contains("main")) {
			out.add(alter + "MAIN " + isMain());
		}
		if (changed.contains("rls")) {
			out.add(alter + "RLS " + AclGrammar.literal(blankToEmpty(getRls())));
		}
		if (changed.contains("columns")) {
			out.add(alter + "COLUMNS " + AclGrammar.literal(blankToEmpty(getColumns())));
		}
		return out;
	}

	static String blankToEmpty(String text) {
		return AclGrammar.isBlank(text) ? "" : text.trim();
	}

	@Override
	public List<String> dropStatements() {
		return List.of("REVOKE CATALOG " + AclGrammar.path(getName()) + " FROM ROLE " + AclGrammar.ident(role()));
	}
}
