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
 * A role's object grant (platform.object_grants). Read: one property per column of its view (spec 007.3). Written
 * (spec 008.1, spec 011): `GRANT OBJECT c.n TO ROLE r [WITH (…) | CAPS '{}'] [RLS '…'] [COLUMNS '…'] [MASK '…']
 * [VERSIONS '…']` - the grammar has no ALTER for it and a GRANT replaces it whole, so a change writes it again with
 * every property the node holds (its masks and versions too), each text the user wrote a literal the node reads with
 * duckdb's parser (duckdb-acl spec 180); `REVOKE OBJECT c.n FROM ROLE r` (duckdb-acl spec 120). A grant whose columns
 * or masks the node could not read (NULL in the view) is never written again: it would show what they hide.
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
	          description = "Which columns the role sees (duckdb-acl spec 158): empty = every column; id, amount = those "
	                        + "only; * EXCLUDE (ssn) = every column but those")
	public String getColumns() {
		return edited("columns", storedColumns());
	}

	public void setColumns(String columns) {
		edit("columns", columns);
	}

	@Property(viewable = true, editable = true, updatable = true, hidden = true, order = 7, name = "Masks",
	          description = "What the role reads in place of a column: ssn AS '***-**-' || right(ssn, 4), salary AS NULL")
	public String getMasks() {
		return edited("masks", storedMasks());
	}

	public void setMasks(String masks) {
		edit("masks", masks);
	}

	@Property(viewable = true, editable = true, updatable = true, hidden = true, order = 8, name = "Versions",
	          description = "Which versions of a time-travel table the role reads: CURRENT, ANY, AT (VERSION => 3), "
	                        + "BETWEEN TIMESTAMP '…' AND TIMESTAMP '…'; empty = the catalog grant's")
	public String getVersions() {
		return edited("versions", text("versions"));
	}

	public void setVersions(String versions) {
		edit("versions", versions);
	}

	/**
	 * The test as the node prints it, from the view's STRUCT(mode, names): empty = every column; null = not read -
	 * NULL, or any shape this does not read whole (fail closed: a test read as "every column" would widen it).
	 */
	private String storedColumns() {
		Map<?, ?> fields = struct(row.get("columns"));
		if (fields == null || !(fields.get("mode") instanceof Object modeValue)) {
			return null;
		}
		String mode = modeValue.toString();
		if (mode.equals("all")) {
			return "";
		}
		List<Object> listed = list(fields.get("names"));
		if (listed == null || listed.isEmpty() || !(mode.equals("only") || mode.equals("except"))) {
			return null;
		}
		List<String> names = new ArrayList<>();
		for (Object name : listed) {
			if (name == null) {
				return null;
			}
			names.add(AclGrammar.fieldIdent(name.toString()));
		}
		return mode.equals("only") ? String.join(", ", names) : "* EXCLUDE (" + String.join(", ", names) + ")";
	}

	/**
	 * The masks as written (`col AS <expr>, …`), from the view's LIST(STRUCT(column, mask)); null = not read whole
	 * (NULL, another shape, an item without its column or mask) - a mask dropped would show its column.
	 */
	private String storedMasks() {
		List<Object> listed = list(row.get("masks"));
		if (listed == null) {
			return null;
		}
		List<String> items = new ArrayList<>();
		for (Object item : listed) {
			Map<?, ?> fields = struct(item);
			Object column = fields == null ? null : fields.get("column");
			Object mask = fields == null ? null : fields.get("mask");
			if (column == null || mask == null) {
				return null;
			}
			items.add(AclGrammar.fieldIdent(column.toString()) + " AS " + mask);
		}
		return String.join(", ", items);
	}

	/** A STRUCT value's fields, or null when it is NULL or not one this reads. */
	private static Map<?, ?> struct(Object value) {
		if (value instanceof java.sql.Struct || value instanceof Map<?, ?>) {
			Map<?, ?> fields = PlatformValues.fieldsOf(value);
			return fields.isEmpty() ? null : fields;
		}
		return null;
	}

	/** A LIST value's items, or null when it is NULL or not one this reads. */
	private static List<Object> list(Object value) {
		try {
			Object items = value instanceof java.sql.Array array ? array.getArray() : value;
			if (items instanceof Object[] || items instanceof java.util.Collection<?>) {
				return PlatformValues.items(items);
			}
		} catch (java.sql.SQLException e) {
			return null;
		}
		return null;
	}

	/**
	 * A stored grant whose columns or masks this cannot read whole: a readable test or set of masks is never NULL
	 * (`all`, `[]`), so NULL is one the node could not read - and a shape the driver gave otherwise is one the plugin
	 * cannot; written again, it would show what they hide.
	 */
	public boolean unreadable() {
		return row.get("role") != null && (storedColumns() == null || storedMasks() == null);
	}

	/**
	 * A GRANT written again replaces the grant whole, so every property is compared with the node's before a save -
	 * not only the ones changed here: a mask written meanwhile would go otherwise. One the node holds now that this
	 * cannot read refuses the save.
	 */
	@Override
	public Map<String, String[]> changedOnTheNode(DBRProgressMonitor monitor, Set<String> changed) throws DBException {
		Set<String> every = new java.util.LinkedHashSet<>(changed);
		every.addAll(List.of("capsUnstated", "rls", "columns", "masks", "versions"));
		Map<String, String[]> moved = super.changedOnTheNode(monitor, every);
		for (String id : List.of("columns", "masks")) {
			if (moved.containsKey(id) && moved.get(id)[1] == null) {
				throw new DBException("duckdb-acl: " + getName() + "'s columns or masks as the node holds them now "
				                      + "cannot be read: written again it would show what they hide - refresh it");
			}
		}
		return moved;
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
			case "columns" -> storedColumns();
			case "masks" -> storedMasks();
			case "versions" -> text("versions");
			default -> null;
		};
	}

	private String grant() throws DBException {
		if (unreadable()) {
			throw new DBException("The node could not read this grant's columns or masks: written again it would show "
			                      + "what they hide - revoke it, or write it in the SQL editor");
		}
		StringBuilder sql = new StringBuilder("GRANT OBJECT ").append(AclGrammar.inCatalog(required(getCatalog(), "the catalog"),
		                                                                                      required(getObject(), "the object")))
		    .append(" TO ROLE ").append(AclGrammar.ident(role())).append(AclGrammar.grantCaps(caps()));
		// every text the user wrote is one literal: the node reads it with duckdb's parser (duckdb-acl spec 180)
		literalClause(sql, "RLS", getRls());
		literalClause(sql, "COLUMNS", getColumns());
		literalClause(sql, "MASK", getMasks());
		literalClause(sql, "VERSIONS", getVersions());
		return sql.toString();
	}

	private static void literalClause(StringBuilder sql, String clause, String text) {
		if (!AclGrammar.isBlank(text)) {
			sql.append(' ').append(clause).append(' ').append(AclGrammar.literal(text.trim()));
		}
	}

	@Override
	public List<String> createStatements() throws DBException {
		return List.of(grant());
	}

	@Override
	public List<String> alterStatements(DBRProgressMonitor monitor, Set<String> changed) throws DBException {
		return changed.isEmpty() ? List.of() : List.of(grant());
	}

	@Override
	public List<String> dropStatements() {
		return List.of("REVOKE OBJECT " + AclGrammar.inCatalog(getCatalog(), getObject()) + " FROM ROLE "
		               + AclGrammar.ident(role()));
	}
}
