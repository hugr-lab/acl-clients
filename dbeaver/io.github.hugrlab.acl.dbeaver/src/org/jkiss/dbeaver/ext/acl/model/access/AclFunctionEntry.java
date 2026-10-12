package org.jkiss.dbeaver.ext.acl.model.access;

import org.jkiss.dbeaver.DBException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.IPropertyValueListProvider;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.meta.PropertyLength;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * A virtual table function or scalar (platform.functions). Read: one property per column of its view (spec 007.3).
 * Written (spec 008.2): a macro `CREATE VIRTUAL TABLE FUNCTION | SCALAR c.n[(params)] [RETURNS …] [PRIMARY KEY …]
 * [COMMENT …] AS <template>` or an alias `… ALIAS OF <physical function> [COMMENT …]`; a change is `ALTER VIRTUAL TABLE
 * FUNCTION | SCALAR c.n SET MACRO <template> | SET ALIAS OF <fn>`, `SET PRIMARY KEY` / `DROP PRIMARY KEY` (a table
 * function), `COMMENT ON VIRTUAL …`; its parameters and result have no ALTER (drop and create); `DROP VIRTUAL TABLE
 * FUNCTION | SCALAR c.n`.
 */
public class AclFunctionEntry extends AclPlatformObject {
	public AclFunctionEntry(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "name";
	}

	@Override
	@Property(viewable = true, editable = true, order = 1)
	public String getName() {
		return super.getName();
	}

	public String catalog() {
		String catalog = text("catalog");
		if (catalog == null && getParentObject() instanceof AclVirtualCatalog parentCatalog) {
			return parentCatalog.getName();
		}
		return catalog;
	}

	@Property(viewable = true, editable = true, order = 2, name = "Kind", listProvider = Kinds.class,
	          description = "table (a table function, called in FROM) or scalar")
	public String getKind() {
		return edited("kind", text("kind") == null ? "table" : text("kind"));
	}

	public void setKind(String kind) {
		edit("kind", kind);
	}

	public boolean isTable() {
		return !"scalar".equals(getKind());
	}

	@Property(viewable = true, editable = true, order = 3, name = "Form", listProvider = Forms.class,
	          description = "macro (a template in physical names: acl_arg(n), acl_claim('x')) or alias (a physical function)")
	public String getForm() {
		return edited("form", text("form") == null ? "macro" : text("form"));
	}

	public void setForm(String form) {
		edit("form", form);
	}

	public boolean isAlias() {
		return "alias".equals(getForm());
	}

	@Property(viewable = true, editable = true, hidden = true, order = 4, name = "Params",
	          description = "The parameter list: threshold INTEGER, tenant VARCHAR (types the probe)")
	public String getParams() {
		return edited("params", text("params"));
	}

	public void setParams(String params) {
		edit("params", params);
	}

	@Property(viewable = true, editable = true, hidden = true, order = 5, name = "Returns",
	          description = "A table function's result columns (id INTEGER, amount INTEGER) or a scalar's type; empty = probed")
	public String getReturns() {
		return edited("returns", "");
	}

	public void setReturns(String returns) {
		edit("returns", returns);
	}

	@Property(viewable = true, editable = true, updatable = true, hidden = true, order = 6, name = "Target",
	          description = "An alias's physical function")
	public String getTarget() {
		return edited("target", text("target"));
	}

	public void setTarget(String target) {
		edit("target", target);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 7, name = "Comment")
	public String getComment() {
		return edited("comment", text("comment"));
	}

	public void setComment(String comment) {
		edit("comment", comment);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 8, name = "Primary key",
	          description = "A table function's declared key over its result: id")
	public String getPrimaryKey(DBRProgressMonitor monitor) throws org.jkiss.dbeaver.DBException {
		return editedOr("primaryKey", () -> storedKey(monitor));
	}

	private String storedKey(DBRProgressMonitor monitor) throws org.jkiss.dbeaver.DBException {
		if (!isTable()) {
			return "";
		}
		List<AclKeyColumn> key = new ArrayList<>(children(monitor, "Key",
		    PlatformSql.view("keys", "catalog", catalog(), "object", getName(), "kind", "table"),
		    r -> new AclKeyColumn(dataSource, this, r)));
		key.sort(java.util.Comparator.comparingInt(c -> {
			try {
				return Integer.parseInt(c.text("pos"));
			} catch (NumberFormatException e) {
				return Integer.MAX_VALUE;
			}
		}));
		List<String> names = new ArrayList<>();
		for (AclKeyColumn column : key) {
			names.add(AclGrammar.ident(column.getName()));
		}
		return String.join(", ", names);
	}

	public void setPrimaryKey(String key) {
		edit("primaryKey", key);
	}

	@Property(viewable = true, order = 9, name = "Created by")
	public String getCreatedBy() {
		return text("created_by");
	}

	@Property(viewable = true, editable = true, updatable = true, hidden = true, order = 50, name = "Template", length = PropertyLength.MULTILINE,
	          description = "A macro's body: a SELECT (table function) or an expression (scalar), in physical names")
	public String getTemplate() {
		return edited("template", text("template"));
	}

	public void setTemplate(String template) {
		edit("template", template);
	}

	@Override
	public boolean isEditable() {
		return true;
	}

	@Override
	public Set<String> functionsFor(Action action) {
		return switch (action) {
			case CREATE -> Set.of("add_table_function", "add_table_function_alias", "add_scalar", "add_scalar_alias");
			case EDIT -> Set.of("alter_function", "set_key", "comment");
			case DELETE -> Set.of("drop_function");
		};
	}

	@Override
	public String catalogOf() {
		return catalog();
	}

	@Override
	protected String selfSql() {
		return PlatformSql.view("functions", "catalog", catalog(), "name", getName(), "kind", getKind());
	}

	@Override
	protected String stored(DBRProgressMonitor monitor, String id) throws org.jkiss.dbeaver.DBException {
		return switch (id) {
			case "primaryKey" -> storedKey(monitor);
			case "template" -> text("template");
			case "target" -> text("target");
			case "comment" -> text("comment");
			default -> null;
		};
	}

	private String kindWord() {
		return isTable() ? "TABLE FUNCTION" : "SCALAR";
	}

	private String target() {
		return AclGrammar.inCatalog(catalog(), getName());
	}

	@Override
	public List<String> createStatements() throws DBException {
		StringBuilder sql = new StringBuilder("CREATE VIRTUAL ").append(kindWord()).append(' ').append(target());
		String comment = getComment();
		if (isAlias()) {
			sql.append(" ALIAS OF ").append(AclGrammar.path(required(getTarget(), "an alias's physical function (Target)")));
			if (!AclGrammar.isBlank(comment)) {
				sql.append(" COMMENT ").append(AclGrammar.literal(comment));
			}
			return List.of(sql.toString());
		}
		if (!AclGrammar.isBlank(getParams())) {
			sql.append('(').append(getParams().trim()).append(')');
		}
		if (!AclGrammar.isBlank(getReturns())) {
			sql.append(isTable() ? " RETURNS TABLE (" + getReturns().trim() + ")" : " RETURNS " + getReturns().trim());
		}
		String key = edited("primaryKey", "");
		if (isTable() && !AclGrammar.isBlank(key)) {
			sql.append(" PRIMARY KEY (").append(key.trim()).append(')');
		}
		if (!AclGrammar.isBlank(comment)) {
			sql.append(" COMMENT ").append(AclGrammar.literal(comment));
		}
		sql.append(" AS ").append(required(getTemplate(), "a macro's template"));
		return List.of(sql.toString());
	}

	@Override
	public List<String> alterStatements(DBRProgressMonitor monitor, Set<String> changed)
	    throws org.jkiss.dbeaver.DBException {
		String alter = "ALTER VIRTUAL " + kindWord() + " " + target();
		List<String> out = new ArrayList<>();
		if (changed.contains("template") && !isAlias()) {
			out.add(alter + " SET MACRO " + getTemplate().trim());
		}
		if (changed.contains("target") && isAlias()) {
			out.add(alter + " SET ALIAS OF " + AclGrammar.path(getTarget().trim()));
		}
		if (changed.contains("primaryKey") && isTable()) {
			String key = getPrimaryKey(monitor);
			out.add(alter + (AclGrammar.isBlank(key) ? " DROP PRIMARY KEY" : " SET PRIMARY KEY (" + key.trim() + ")"));
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

	public static class Kinds implements IPropertyValueListProvider<AclFunctionEntry> {
		@Override
		public boolean allowCustomValue() {
			return false;
		}

		@Override
		public Object[] getPossibleValues(AclFunctionEntry object) {
			return new Object[] {"table", "scalar"};
		}
	}

	public static class Forms implements IPropertyValueListProvider<AclFunctionEntry> {
		@Override
		public boolean allowCustomValue() {
			return false;
		}

		@Override
		public Object[] getPossibleValues(AclFunctionEntry object) {
			return new Object[] {"macro", "alias"};
		}
	}
}
