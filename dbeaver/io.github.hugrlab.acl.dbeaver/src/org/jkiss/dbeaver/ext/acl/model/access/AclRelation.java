package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.IPropertyValueListProvider;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.meta.PropertyLength;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * A virtual table or view (platform.relations). Read: one property per column of its view (spec 007.3). Written (spec
 * 008.1): a table by `CREATE VIRTUAL TABLE c.n AS <phys> [COLUMNS (…)] [RLS (…)] [PRIMARY KEY (…)] [COMMENT '…']`, a
 * view by `CREATE VIRTUAL VIEW c.n [PRIMARY KEY (…)] [COMMENT '…'] AS <select>`, the types (if set) by `SET TYPES`
 * after it; a change is one statement per property - `ALTER VIRTUAL TABLE c.n SET PHYS | COLUMNS | RLS | PRIMARY KEY`
 * (or `DROP PRIMARY KEY`), `ALTER VIRTUAL VIEW c.n SET AS`, `SET TYPES (aliases = …, enums = …)` (`default` = the
 * node's), `COMMENT ON VIRTUAL TABLE | VIEW c.n IS '…'`; dropped by `DROP VIRTUAL TABLE | VIEW c.n`. The declared
 * columns are read from platform.relation_columns and the key from platform.keys.
 */
public class AclRelation extends AclPlatformObject {
	public AclRelation(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "name";
	}

	@Override
	@Property(viewable = true, editable = true, order = 1,
	          description = "The name in its catalog; a nested schema's object by its path: raw.eu.events")
	public String getName() {
		return super.getName();
	}

	/** The catalog it is in: the row's, or - a new one - the catalog it is made under. */
	public String catalog() {
		String catalog = text("catalog");
		if (catalog == null && getParentObject() instanceof AclVirtualCatalog parentCatalog) {
			return parentCatalog.getName();
		}
		return catalog;
	}

	public boolean isView() {
		return "view".equals(getKind());
	}

	@Property(viewable = true, editable = true, order = 2, name = "Kind", listProvider = Kinds.class,
	          description = "table (over a physical object) or view (a SELECT in physical names)")
	public String getKind() {
		String form = text("form");
		return edited("kind", form == null ? "table" : "view".equals(form) ? "view" : "table");
	}

	public void setKind(String kind) {
		edit("kind", kind);
	}

	@Property(viewable = true, order = 3, name = "Form")
	public String getForm() {
		return text("form");
	}

	@Property(viewable = true, editable = true, updatable = true, order = 4, name = "Phys",
	          description = "A table's physical object: db.schema.table")
	public String getPhys() {
		return edited("phys", text("phys"));
	}

	public void setPhys(String phys) {
		edit("phys", phys);
	}

	@Property(viewable = true, editable = true, updatable = true, hidden = true, order = 5, name = "Columns",
	          description = "A table's declared columns, as COLUMNS (…) writes them: id, amount, ssn = NULL, total = amount * 2, "
	                        + "id NOT NULL; empty = every column of the physical object")
	public String getDeclaredColumns(DBRProgressMonitor monitor) throws DBException {
		return editedOr("declaredColumns", () -> storedColumns(monitor));
	}

	public void setDeclaredColumns(String columns) {
		edit("declaredColumns", columns);
	}

	@Property(viewable = true, editable = true, updatable = true, hidden = true, order = 6, name = "Rls",
	          description = "A table's predicate over the physical columns, AND-ed into every read and write")
	public String getRls() {
		return edited("rls", text("rls"));
	}

	public void setRls(String rls) {
		edit("rls", rls);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 7, name = "Primary key",
	          description = "The declared key's columns (declared, never enforced): id, tenant")
	public String getPrimaryKey(DBRProgressMonitor monitor) throws DBException {
		return editedOr("primaryKey", () -> storedKey(monitor));
	}

	public void setPrimaryKey(String key) {
		edit("primaryKey", key);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 8, name = "Comment")
	public String getComment() {
		return edited("comment", text("comment"));
	}

	public void setComment(String comment) {
		edit("comment", comment);
	}

	@Property(viewable = true, order = 9, name = "Origin")
	public String getOrigin() {
		return text("origin");
	}

	@Property(viewable = true, order = 10, name = "Created by")
	public String getCreatedBy() {
		return text("created_by");
	}

	@Property(viewable = true, editable = true, updatable = true, order = 50, name = "View sql", length = PropertyLength.MULTILINE,
	          description = "A view's SELECT, in physical names")
	public String getViewSql() {
		return edited("viewSql", text("view_sql"));
	}

	public void setViewSql(String sql) {
		edit("viewSql", sql);
	}

	@Property(viewable = true, order = 51, name = "Rls checked")
	public String getRlsChecked() {
		return text("rls_checked");
	}

	@Property(viewable = true, editable = true, updatable = true, order = 52, name = "Alias types", listProvider = AliasTypes.class,
	          description = "An extension's alias type as its base type (base) or kept (keep); empty = the node's setting")
	public String getAliasTypes() {
		return edited("aliasTypes", text("alias_types"));
	}

	public void setAliasTypes(String value) {
		edit("aliasTypes", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 53, name = "Enum types", listProvider = EnumTypes.class,
	          description = "An ENUM kept (keep) or as VARCHAR (varchar); empty = the node's setting")
	public String getEnumTypes() {
		return edited("enumTypes", text("enum_types"));
	}

	public void setEnumTypes(String value) {
		edit("enumTypes", value);
	}

	// --- what is stored -------------------------------------------------------------------------------------------

	private String storedColumns(DBRProgressMonitor monitor) throws DBException {
		List<String> items = new ArrayList<>();
		List<AclRelationColumn> columns = new ArrayList<>(getColumns(monitor));
		columns.sort(Comparator.comparingInt(c -> pos(c.text("pos"))));
		for (AclRelationColumn column : columns) {
			StringBuilder item = new StringBuilder(AclGrammar.ident(column.getName()));
			String expr = column.text("expr");
			if (!AclGrammar.isBlank(expr)) {
				item.append(" = ").append(expr);
			}
			String nullable = column.text("nullable");
			if ("false".equals(nullable)) {
				item.append(" NOT NULL");
			} else if ("true".equals(nullable)) {
				item.append(" NULL");
			}
			items.add(item.toString());
		}
		return String.join(", ", items);
	}

	private String storedKey(DBRProgressMonitor monitor) throws DBException {
		List<AclKeyColumn> key = new ArrayList<>(getKeyColumns(monitor));
		key.sort(Comparator.comparingInt(c -> pos(c.text("pos"))));
		List<String> names = new ArrayList<>();
		for (AclKeyColumn column : key) {
			names.add(AclGrammar.ident(column.getName()));
		}
		return String.join(", ", names);
	}

	private static int pos(String text) {
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException e) {
			return Integer.MAX_VALUE;
		}
	}

	@Override
	public boolean isEditable() {
		return true;
	}

	@Override
	public Set<String> functionsFor(Action action) {
		return switch (action) {
			case CREATE -> Set.of("add_relation", "add_view");
			case EDIT -> Set.of("alter_relation", "set_key", "comment");
			case DELETE -> Set.of("drop_relation");
		};
	}

	@Override
	public String catalogOf() {
		return catalog();
	}

	@Override
	protected String selfSql() {
		return PlatformSql.view("relations", "catalog", catalog(), "name", getName());
	}

	@Override
	protected String stored(DBRProgressMonitor monitor, String id) throws DBException {
		return switch (id) {
			case "phys" -> text("phys");
			case "declaredColumns" -> storedColumns(monitor);
			case "rls" -> text("rls");
			case "primaryKey" -> storedKey(monitor);
			case "comment" -> text("comment");
			case "viewSql" -> text("view_sql");
			case "aliasTypes" -> text("alias_types");
			case "enumTypes" -> text("enum_types");
			default -> null;
		};
	}

	// --- the statements -------------------------------------------------------------------------------------------

	private String target() {
		return AclGrammar.inCatalog(catalog(), getName());
	}

	private String kindWord() {
		return isView() ? "VIEW" : "TABLE";
	}

	private String typesStatement() {
		return "ALTER VIRTUAL " + kindWord() + " " + target() + " SET TYPES (aliases = " + typeValue(getAliasTypes())
		       + ", enums = " + typeValue(getEnumTypes()) + ")";
	}

	private static String typeValue(String value) {
		return AclGrammar.isBlank(value) ? "default" : value.trim();
	}

	@Override
	public List<String> createStatements() throws DBException {
		List<String> out = new ArrayList<>();
		String key = edited("primaryKey", "");
		String comment = getComment();
		StringBuilder sql = new StringBuilder("CREATE VIRTUAL ").append(kindWord()).append(' ').append(target());
		if (isView()) {
			if (!AclGrammar.isBlank(key)) {
				sql.append(" PRIMARY KEY (").append(key.trim()).append(')');
			}
			if (!AclGrammar.isBlank(comment)) {
				sql.append(" COMMENT ").append(AclGrammar.literal(comment));
			}
			sql.append(" AS ").append(required(getViewSql(), "a view's SELECT"));
		} else {
			sql.append(" AS ").append(AclGrammar.path(required(getPhys(), "the physical object (Phys)")));
			String columns = edited("declaredColumns", "");
			if (!AclGrammar.isBlank(columns)) {
				sql.append(" COLUMNS (").append(columns.trim()).append(')');
			}
			if (!AclGrammar.isBlank(getRls())) {
				sql.append(" RLS (").append(getRls().trim()).append(')');
			}
			if (!AclGrammar.isBlank(key)) {
				sql.append(" PRIMARY KEY (").append(key.trim()).append(')');
			}
			if (!AclGrammar.isBlank(comment)) {
				sql.append(" COMMENT ").append(AclGrammar.literal(comment));
			}
		}
		out.add(sql.toString());
		if (!AclGrammar.isBlank(getAliasTypes()) || !AclGrammar.isBlank(getEnumTypes())) {
			out.add(typesStatement());
		}
		return out;
	}

	@Override
	public List<String> alterStatements(DBRProgressMonitor monitor, Set<String> changed) throws DBException {
		String alter = "ALTER VIRTUAL " + kindWord() + " " + target();
		List<String> out = new ArrayList<>();
		if (changed.contains("phys")) {
			out.add(alter + " SET PHYS " + AclGrammar.path(getPhys().trim()));
		}
		if (changed.contains("declaredColumns")) {
			String columns = getDeclaredColumns(monitor);
			out.add(alter + " SET COLUMNS " + (AclGrammar.isBlank(columns) ? "''" : "(" + columns.trim() + ")"));
		}
		if (changed.contains("rls")) {
			out.add(alter + " SET RLS " + (AclGrammar.isBlank(getRls()) ? "''" : "(" + getRls().trim() + ")"));
		}
		if (changed.contains("viewSql")) {
			out.add(alter + " SET AS " + getViewSql().trim());
		}
		if (changed.contains("primaryKey")) {
			String key = getPrimaryKey(monitor);
			out.add(alter + (AclGrammar.isBlank(key) ? " DROP PRIMARY KEY" : " SET PRIMARY KEY (" + key.trim() + ")"));
		}
		if (changed.contains("aliasTypes") || changed.contains("enumTypes")) {
			out.add(typesStatement());
		}
		if (changed.contains("comment")) {
			out.add("COMMENT ON VIRTUAL " + kindWord() + " " + target() + " IS " + AclGrammar.literal(getComment()));
		}
		return out;
	}

	@Override
	public List<String> dropStatements() {
		return List.of("DROP VIRTUAL " + kindWord() + " " + target());
	}

	/**
	 * The physical columns of a table's source (name, type), as the node lists its attached sources to an administrator
	 * (`platform.attached.<db>.<schema>`, duckdb-acl spec 118.3); empty for a view or a source it does not list.
	 */
	public List<String[]> physicalColumns(DBRProgressMonitor monitor) throws DBException {
		List<String[]> out = new ArrayList<>();
		java.util.List<String> parts = getPhys() == null ? null : org.jkiss.dbeaver.ext.acl.model.SchemaPath.parts(getPhys().trim());
		if (isView() || parts == null || parts.size() != 3) {
			return out;
		}
		String schema = org.jkiss.dbeaver.ext.acl.model.SchemaPath.join(List.of("attached", parts.get(0), parts.get(1)));
		String sql = "SELECT column_name, data_type FROM information_schema.columns WHERE table_catalog = 'platform'"
		             + " AND table_schema = " + PlatformSql.literal(schema) + " AND table_name = "
		             + PlatformSql.literal(parts.get(2)) + " ORDER BY ordinal_position";
		for (Map<String, Object> row : dataSource.platformRows(monitor, sql)) {
			out.add(new String[] {PlatformValues.text(row.get("column_name")), PlatformValues.text(row.get("data_type"))});
		}
		return out;
	}

	/**
	 * What the navigator shows under Columns: the declared list, or - a relation that declares none (an alias, an
	 * expansion's record) - its source's columns with their types, as the node lists its attached sources.
	 */
	@Association
	public List<AclRelationColumn> getExposedColumns(DBRProgressMonitor monitor) throws DBException {
		List<AclRelationColumn> declared = getColumns(monitor);
		if (!declared.isEmpty() || isView()) {
			return declared;
		}
		return childrenOf("SourceColumns", () -> {
			List<AclRelationColumn> out = new ArrayList<>();
			int pos = 0;
			for (String[] column : physicalColumns(monitor)) {
				Map<String, Object> made = new java.util.HashMap<>();
				made.put("name", column[0]);
				made.put("type", column[1]);
				made.put("pos", pos++);
				out.add(new AclRelationColumn(dataSource, this, made));
			}
			return out;
		});
	}

	@Association
	public List<AclRelationColumn> getColumns(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "Columns", PlatformSql.view("relation_columns", "catalog", catalog(), "relation", getName()), r -> new AclRelationColumn(dataSource, this, r));
	}

	@Association
	public List<AclKeyColumn> getKeyColumns(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "KeyColumns", PlatformSql.view("keys", "catalog", catalog(), "object", getName()), r -> new AclKeyColumn(dataSource, this, r));
	}

	/** The two kinds Create offers. */
	public static class Kinds implements IPropertyValueListProvider<AclRelation> {
		@Override
		public boolean allowCustomValue() {
			return false;
		}

		@Override
		public Object[] getPossibleValues(AclRelation object) {
			return new Object[] {"table", "view"};
		}
	}

	/** An alias type's exposure: the node's setting (empty), base or keep. */
	public static class AliasTypes implements IPropertyValueListProvider<AclRelation> {
		@Override
		public boolean allowCustomValue() {
			return false;
		}

		@Override
		public Object[] getPossibleValues(AclRelation object) {
			return new Object[] {"", "base", "keep"};
		}
	}

	/** An ENUM's exposure: the node's setting (empty), keep or varchar. */
	public static class EnumTypes implements IPropertyValueListProvider<AclRelation> {
		@Override
		public boolean allowCustomValue() {
			return false;
		}

		@Override
		public Object[] getPossibleValues(AclRelation object) {
			return new Object[] {"", "keep", "varchar"};
		}
	}
}
