package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * A role's object grant (platform.object_grants). Read: one property per column of its view (spec 007.3). Written
 * (spec 008.1): `GRANT OBJECT c.n TO ROLE r [WITH (…) | CAPS '{}'] [RLS (…)] [COLUMNS (…)]` - the grammar has no ALTER
 * for it and a GRANT replaces it whole (verified), so a change writes it again with every property the form shows,
 * which are all of its facts; `REVOKE OBJECT c.n FROM ROLE r` (duckdb-acl spec 120).
 */
public class AclObjectGrant extends AclGrantObject implements AclPolicyGrant {
	public AclObjectGrant(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "object";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		String catalog = getCatalog();
		String object = getObject();
		return catalog == null || object == null ? "" : catalog + "." + object;
	}

	@Property(viewable = true, editable = true, order = 2, name = "Catalog", listProvider = AclCatalogNames.class)
	public String getCatalog() {
		return edited("catalog", text("catalog"));
	}

	public void setCatalog(String catalog) {
		edit("catalog", catalog);
	}

	@Property(viewable = true, editable = true, order = 3, name = "Object",
	          description = "A table, view, table function or scalar of the catalog, by its name there: orders, raw.eu.events")
	public String getObject() {
		return edited("object", text("object"));
	}

	public void setObject(String object) {
		edit("object", object);
	}

	@Property(viewable = true, order = 4, name = "Caps")
	public String getCaps() {
		Set<String> caps = caps();
		return caps == null ? "unstated: the catalog grant's" : caps.isEmpty() ? "none" : capsText(caps);
	}

	@Property(viewable = true, editable = true, updatable = true, hidden = true, order = 5, name = "Rls",
	          description = "The grant's predicate, AND-ed onto the catalog grant's and the object's own")
	public String getRls() {
		return edited("rls", text("rls"));
	}

	public void setRls(String rls) {
		edit("rls", rls);
	}

	@Property(viewable = true, editable = true, updatable = true, hidden = true, order = 6, name = "Columns",
	          description = "The grant's column list, as COLUMNS (…) writes it: id, amount, ssn = NULL, address.city")
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
	public String grantCatalog() {
		return getCatalog();
	}

	@Override
	public String grantObject() {
		return getObject();
	}

	@Override
	public Set<String> functionsFor(Action action) {
		return switch (action) {
			case CREATE -> Set.of("grant_object");
			case EDIT -> Set.of("grant_object");
			case DELETE -> Set.of("revoke_object");
		};
	}

	@Override
	public String catalogOf() {
		return getCatalog();
	}

	@Override
	protected String selfSql() {
		return PlatformSql.view("object_grants", "role", role(), "catalog", getCatalog(), "object", getObject());
	}

	@Override
	protected String stored(DBRProgressMonitor monitor, String id) {
		if (isCapsProperty(id)) {
			return capsText(storedCaps());
		}
		return switch (id) {
			case "rls" -> text("rls");
			case "columns" -> text("columns");
			default -> null;
		};
	}

	private String grant() {
		StringBuilder sql = new StringBuilder("GRANT OBJECT ").append(AclGrammar.inCatalog(getCatalog(), getObject()))
		    .append(" TO ROLE ").append(AclGrammar.ident(role())).append(AclGrammar.grantCaps(caps()));
		if (!AclGrammar.isBlank(getRls())) {
			sql.append(" RLS (").append(getRls().trim()).append(')');
		}
		if (!AclGrammar.isBlank(getColumns())) {
			sql.append(" COLUMNS (").append(getColumns().trim()).append(')');
		}
		return sql.toString();
	}

	@Override
	public List<String> createStatements() {
		return List.of(grant());
	}

	@Override
	public List<String> alterStatements(DBRProgressMonitor monitor, Set<String> changed) {
		return changed.isEmpty() ? List.of() : List.of(grant());
	}

	@Override
	public List<String> dropStatements() {
		return List.of("REVOKE OBJECT " + AclGrammar.inCatalog(getCatalog(), getObject()) + " FROM ROLE "
		               + AclGrammar.ident(role()));
	}
}
