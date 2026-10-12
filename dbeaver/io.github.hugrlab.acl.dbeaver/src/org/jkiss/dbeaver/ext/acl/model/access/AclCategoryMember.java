package org.jkiss.dbeaver.ext.acl.model.access;

import org.jkiss.dbeaver.DBException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * A function in a category (platform.function_category_members). Written (spec 008.2): `ALTER FUNCTION CATEGORY c ADD
 * (<function>)` / `DROP (<function>)` - a function as the grammar names it: `name`, `schema.name`, `db.schema.name`,
 * `TABLE` after a table function's; the never set is refused by the node.
 */
public class AclCategoryMember extends AclPlatformObject {
	public AclCategoryMember(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "name";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return text("name") == null ? edited("function", "") : text("database") + "." + text("schema") + "." + text("name")
		                                                       + " [" + text("kind") + "]";
	}

	/** The function as the grammar names it: `system.main.lower`, `system.main.range TABLE`. */
	@Property(viewable = true, editable = true, order = 2, name = "Function",
	          description = "lower, system.main.upper, range TABLE, \"+\" - TABLE after a table function")
	public String getFunction() {
		if (text("name") == null) {
			return edited("function", "");
		}
		return AclGrammar.ident(text("database")) + "." + AclGrammar.ident(text("schema")) + "." + AclGrammar.ident(text("name"))
		       + ("table".equals(text("kind")) ? " TABLE" : "");
	}

	public void setFunction(String function) {
		edit("function", function);
	}

	@Property(viewable = true, order = 3, name = "Kind")
	public String getKind() {
		return text("kind");
	}

	private String category() {
		return getParentObject() instanceof AclFunctionCategory category ? category.getName() : text("category");
	}

	@Override
	public boolean isEditable() {
		return true;
	}

	@Override
	public Set<String> functionsFor(Action action) {
		return switch (action) {
			case CREATE -> Set.of("function_category_add");
			case EDIT -> Set.of();
			case DELETE -> Set.of("function_category_remove");
		};
	}

	@Override
	public List<String> createStatements() throws DBException {
		return List.of("ALTER FUNCTION CATEGORY " + AclGrammar.ident(category()) + " ADD (" + getFunction().trim() + ")");
	}

	@Override
	public List<String> dropStatements() {
		return List.of("ALTER FUNCTION CATEGORY " + AclGrammar.ident(category()) + " DROP (" + getFunction() + ")");
	}
}
