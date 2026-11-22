package org.jkiss.dbeaver.ext.acl.model.access;

import org.jkiss.dbeaver.DBException;

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
 * 008.1, spec 011): `GRANT CATALOG c TO ROLE r [WITH (…) | CAPS '{}'] [MAIN] [RLS '…']`; a change is one `ALTER GRANT
 * CATALOG c TO ROLE r SET CAPS | MAIN | RLS` per property, so what the plugin does not edit (its versions) stays;
 * `REVOKE CATALOG c FROM ROLE r` (which takes the role's object grants in that catalog with it - the node's rule). A
 * catalog grant states no columns since duckdb-acl spec 158: a list an older build stored refuses every read under the
 * grant, and is only cleared here (`SET COLUMNS ''`).
 */
public class AclCatalogGrant extends AclGrantObject implements AclPolicyGrant {
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

	@Property(viewable = true, editable = true, updatable = true, hidden = true, order = 4, name = "Rls",
	          description = "The grant's own predicate over the physical columns, AND-ed onto every object of the catalog")
	public String getRls() {
		return edited("rls", text("rls"));
	}

	public void setRls(String rls) {
		edit("rls", rls);
	}

	@Property(viewable = true, editable = true, updatable = true, hidden = true, order = 5, name = "Legacy columns",
	          description = "A column list an older build stored: since duckdb-acl spec 158 it refuses every read under "
	                        + "the grant - clear it; which columns a role sees is the object grant's")
	public String getColumns() {
		return edited("columns", text("columns"));
	}

	public void setColumns(String columns) {
		edit("columns", columns);
	}

	@Property(viewable = true, order = 6, name = "Versions",
	          description = "Which versions of a time-travel table the role reads; empty = the current one (duckdb-acl "
	                        + "spec 168). Changed from the SQL editor: GRANT CATALOG … VERSIONS …")
	public String getVersions() {
		return text("versions");
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
	public String grantCatalog() {
		return getName();
	}

	@Override
	public String grantObject() {
		return "";
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
	public List<String> createStatements() throws DBException {
		required(getName(), "the catalog");
		StringBuilder sql = new StringBuilder("GRANT CATALOG ").append(target()).append(AclGrammar.grantCaps(caps()));
		if (isMain()) {
			sql.append(" MAIN");
		}
		if (!AclGrammar.isBlank(getRls())) {
			sql.append(" RLS ").append(AclGrammar.literal(getRls().trim()));
		}
		noColumns();
		return List.of(sql.toString());
	}

	@Override
	public List<String> alterStatements(DBRProgressMonitor monitor, Set<String> changed) throws DBException {
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
			noColumns();
			out.add(alter + "COLUMNS ''");
		}
		return out;
	}

	/** A catalog grant takes no column list (duckdb-acl spec 158): a legacy one is only cleared. */
	private void noColumns() throws DBException {
		if (!AclGrammar.isBlank(getColumns())) {
			throw new DBException("A catalog grant takes no column list (duckdb-acl spec 158): clear it - which columns "
			                      + "a role sees, and masked how, is said on the object's grant");
		}
	}

	static String blankToEmpty(String text) {
		return AclGrammar.isBlank(text) ? "" : text.trim();
	}

	@Override
	public List<String> dropStatements() {
		return List.of("REVOKE CATALOG " + AclGrammar.path(getName()) + " FROM ROLE " + AclGrammar.ident(role()));
	}
}
