package org.jkiss.dbeaver.ext.acl.model.access;

import org.jkiss.dbeaver.DBException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.IPropertyValueListProvider;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * A declared reference (platform.references, duckdb-acl spec 022). Read: one property per column of its view (spec
 * 007.3). Written (spec 008.2): `CREATE VIRTUAL REFERENCE c.n FROM <a> TO [FUNCTION] <b>[(args)] [ON (pairs) | ON
 * EXPRESSION '…'] [CARDINALITY …] [OPTIONAL] [JOIN …] [COMMENT …]`; its hints change in place - `ALTER VIRTUAL REFERENCE
 * c.n SET COMMENT | SET CARDINALITY | SET OPTIONAL` (spec 120) - its ends and condition only by a re-create; `DROP
 * VIRTUAL REFERENCE c.n`.
 */
public class AclReferenceEntry extends AclPlatformObject {
	public AclReferenceEntry(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
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

	@Property(viewable = true, editable = true, order = 2, name = "From object", listProvider = AclObjectNames.class)
	public String getFromObject() {
		return edited("fromObject", text("from_object"));
	}

	public void setFromObject(String value) {
		edit("fromObject", value);
	}

	@Property(viewable = true, editable = true, order = 3, name = "To object", listProvider = AclObjectNames.class)
	public String getToObject() {
		return edited("toObject", text("to_object"));
	}

	public void setToObject(String value) {
		edit("toObject", value);
	}

	@Property(viewable = true, editable = true, order = 4, name = "To kind", listProvider = ToKinds.class,
	          description = "relation (a table or view) or function (a table function)")
	public String getToKind() {
		return edited("toKind", text("to_kind") == null ? "relation" : text("to_kind"));
	}

	public void setToKind(String value) {
		edit("toKind", value);
	}

	@Property(viewable = true, editable = true, order = 5, name = "On",
	          description = "The column pairs: customer_id = id (a function end: its result columns)")
	public String getPairs() {
		return edited("pairs", "");
	}

	public void setPairs(String value) {
		edit("pairs", value);
	}

	@Property(viewable = true, editable = true, order = 6, name = "Arguments",
	          description = "A function end's argument substitution: cust => id")
	public String getArguments() {
		return edited("arguments", "");
	}

	public void setArguments(String value) {
		edit("arguments", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 7, name = "Cardinality",
	          listProvider = Cardinalities.class)
	public String getCardinality() {
		return edited("cardinality", text("cardinality"));
	}

	public void setCardinality(String value) {
		edit("cardinality", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 8, name = "Optional",
	          description = "The far side may be absent")
	public boolean isOptional() {
		return edited("optional", Boolean.TRUE.equals(row.get("optional")));
	}

	public void setOptional(boolean value) {
		edit("optional", value);
	}

	@Property(viewable = true, editable = true, order = 9, name = "Join method", listProvider = JoinMethods.class)
	public String getJoinMethod() {
		return edited("joinMethod", text("join_method"));
	}

	public void setJoinMethod(String value) {
		edit("joinMethod", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 10, name = "Comment")
	public String getComment() {
		return edited("comment", text("comment"));
	}

	public void setComment(String value) {
		edit("comment", value);
	}

	@Property(viewable = true, editable = true, order = 50, name = "Expression",
	          description = "ON EXPRESSION: SQL in which every column is qualified by its end - instead of the pairs")
	public String getExpression() {
		return edited("expression", text("expression"));
	}

	public void setExpression(String value) {
		edit("expression", value);
	}

	@Override
	public boolean isEditable() {
		return true;
	}

	@Override
	public Set<String> functionsFor(Action action) {
		return switch (action) {
			case CREATE -> Set.of("add_reference");
			case EDIT -> Set.of("alter_reference");
			case DELETE -> Set.of("drop_reference");
		};
	}

	@Override
	public String catalogOf() {
		return catalog();
	}

	@Override
	protected String selfSql() {
		return PlatformSql.view("references", "catalog", catalog(), "name", getName());
	}

	@Override
	protected String stored(DBRProgressMonitor monitor, String id) {
		return switch (id) {
			case "cardinality" -> text("cardinality");
			case "optional" -> String.valueOf(Boolean.TRUE.equals(row.get("optional")));
			case "comment" -> text("comment");
			default -> null;
		};
	}

	private String target() {
		return AclGrammar.inCatalog(catalog(), getName());
	}

	@Override
	public List<String> createStatements() throws DBException {
		boolean function = "function".equals(getToKind());
		StringBuilder sql = new StringBuilder("CREATE VIRTUAL REFERENCE ").append(target()).append(" FROM ")
		    .append(AclGrammar.path(required(getFromObject(), "the From object"))).append(" TO ").append(function ? "FUNCTION " : "")
		    .append(AclGrammar.path(required(getToObject(), "the To object")));
		if (function && !AclGrammar.isBlank(getArguments())) {
			sql.append('(').append(getArguments().trim()).append(')');
		}
		if (!AclGrammar.isBlank(getExpression())) {
			sql.append(" ON EXPRESSION ").append(AclGrammar.literal(getExpression().trim()));
		} else if (!AclGrammar.isBlank(getPairs())) {
			sql.append(" ON (").append(getPairs().trim()).append(')');
		}
		if (!AclGrammar.isBlank(getCardinality())) {
			sql.append(" CARDINALITY ").append(getCardinality().trim());
		}
		if (isOptional()) {
			sql.append(" OPTIONAL");
		}
		if (!AclGrammar.isBlank(getJoinMethod())) {
			sql.append(" JOIN ").append(getJoinMethod().trim());
		}
		if (!AclGrammar.isBlank(getComment())) {
			sql.append(" COMMENT ").append(AclGrammar.literal(getComment()));
		}
		return List.of(sql.toString());
	}

	private static String blank(String text) {
		return text == null ? "" : text.trim();
	}

	@Override
	public List<String> alterStatements(DBRProgressMonitor monitor, Set<String> changed) {
		String alter = "ALTER VIRTUAL REFERENCE " + target() + " SET ";
		List<String> out = new ArrayList<>();
		if (changed.contains("cardinality") && !AclGrammar.isBlank(getCardinality())) {
			out.add(alter + "CARDINALITY " + getCardinality().trim());
		}
		if (changed.contains("optional")) {
			out.add(alter + "OPTIONAL " + isOptional());
		}
		if (changed.contains("comment")) {
			out.add(alter + "COMMENT " + AclGrammar.literal(getComment()));
		}
		return out;
	}

	@Override
	public List<String> dropStatements() {
		return List.of("DROP VIRTUAL REFERENCE " + target());
	}

	public static class ToKinds implements IPropertyValueListProvider<AclReferenceEntry> {
		@Override
		public boolean allowCustomValue() {
			return false;
		}

		@Override
		public Object[] getPossibleValues(AclReferenceEntry object) {
			return new Object[] {"relation", "function"};
		}
	}

	public static class Cardinalities implements IPropertyValueListProvider<AclReferenceEntry> {
		@Override
		public boolean allowCustomValue() {
			return false;
		}

		@Override
		public Object[] getPossibleValues(AclReferenceEntry object) {
			return new Object[] {"", "many_to_one", "one_to_many", "one_to_one", "many_to_many"};
		}
	}

	public static class JoinMethods implements IPropertyValueListProvider<AclReferenceEntry> {
		@Override
		public boolean allowCustomValue() {
			return false;
		}

		@Override
		public Object[] getPossibleValues(AclReferenceEntry object) {
			return new Object[] {"", "asof", "positional"};
		}
	}
}
