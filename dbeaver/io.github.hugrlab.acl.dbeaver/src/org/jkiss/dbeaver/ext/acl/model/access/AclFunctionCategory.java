package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * A function category (platform.function_categories, duckdb-acl spec 072). Read: one property per column of its view
 * (spec 007.3). Written (spec 008.2): `CREATE FUNCTION CATEGORY c [COMMENT …]`, `ALTER FUNCTION CATEGORY c SET COMMENT`
 * (spec 120), `DROP FUNCTION CATEGORY c`; its members are added and dropped as members (AclCategoryMember).
 */
public class AclFunctionCategory extends AclPlatformObject {
	public AclFunctionCategory(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "category";
	}

	@Override
	@Property(viewable = true, editable = true, order = 1)
	public String getName() {
		return super.getName();
	}

	@Property(viewable = true, editable = true, updatable = true, order = 2, name = "Comment")
	public String getComment() {
		return edited("comment", text("comment"));
	}

	public void setComment(String comment) {
		edit("comment", comment);
	}

	@Property(viewable = true, order = 3, name = "Builtin")
	public String getBuiltin() {
		return text("builtin");
	}

	@Override
	public boolean isEditable() {
		return true;
	}

	@Override
	public Set<String> functionsFor(Action action) {
		return switch (action) {
			case CREATE -> Set.of("create_function_category");
			case EDIT -> Set.of("comment_function_category");
			case DELETE -> Set.of("drop_function_category");
		};
	}

	@Override
	protected String selfSql() {
		return PlatformSql.view("function_categories", "category", getName());
	}

	@Override
	protected String stored(DBRProgressMonitor monitor, String id) {
		return "comment".equals(id) ? text("comment") : null;
	}

	@Override
	public List<String> createStatements() {
		String comment = getComment();
		return List.of("CREATE FUNCTION CATEGORY " + AclGrammar.ident(getName())
		               + (AclGrammar.isBlank(comment) ? "" : " COMMENT " + AclGrammar.literal(comment)));
	}

	@Override
	public List<String> alterStatements(DBRProgressMonitor monitor, Set<String> changed) {
		List<String> out = new ArrayList<>();
		if (changed.contains("comment")) {
			out.add("ALTER FUNCTION CATEGORY " + AclGrammar.ident(getName()) + " SET COMMENT "
			        + AclGrammar.literal(getComment() == null ? "" : getComment()));
		}
		return out;
	}

	@Override
	public List<String> dropStatements() {
		return List.of("DROP FUNCTION CATEGORY " + AclGrammar.ident(getName()));
	}

	@Association
	public List<AclCategoryMember> getMembers(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "Members", PlatformSql.view("function_category_members", "category", getName()), r -> new AclCategoryMember(dataSource, this, r));
	}
}
