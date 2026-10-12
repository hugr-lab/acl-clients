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
 * A virtual schema (platform.schemas). Read: one property per column of its view (spec 007.3). Written (spec 008.2):
 * a live alias `CREATE VIRTUAL SCHEMA c.path AS <phys schema> [COMMENT]` or an expansion `… FROM <phys schema>` (one
 * record per object, read once); `ALTER VIRTUAL SCHEMA c.path SET PHYS` (an alias), `COMMENT ON VIRTUAL SCHEMA`;
 * `DROP VIRTUAL SCHEMA c.path` - without CASCADE: an expansion's records stay unless the node is told otherwise.
 * Refresh (an expansion re-read from its source) is an action of its own.
 */
public class AclVirtualSchema extends AclPlatformObject implements Operable {
	public AclVirtualSchema(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "path";
	}

	@Override
	@Property(viewable = true, editable = true, order = 1, description = "The schema's path in its catalog: raw or raw.eu")
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

	/** alias (live: a name under it resolves through it) or expansion (one record per object, read once). */
	@Property(viewable = true, editable = true, order = 2, name = "Kind", listProvider = Kinds.class)
	public String getKind() {
		boolean stored = row.containsKey("path");
		String kind = !stored ? "alias" : text("phys_path") != null ? "alias" : "expansion";
		return edited("kind", kind);
	}

	public void setKind(String kind) {
		edit("kind", kind);
	}

	public boolean isExpansion() {
		return "expansion".equals(getKind());
	}

	@Property(viewable = true, editable = true, updatable = true, order = 3, name = "Phys path",
	          description = "The physical schema: db.schema (an expansion's is fixed once read)")
	public String getPhysPath() {
		return edited("physPath", text("phys_path") != null ? text("phys_path") : text("origin"));
	}

	public void setPhysPath(String path) {
		edit("physPath", path);
	}

	@Property(viewable = true, order = 4, name = "Origin")
	public String getOrigin() {
		return text("origin");
	}

	@Property(viewable = true, editable = true, updatable = true, order = 5, name = "Comment")
	public String getComment() {
		return edited("comment", text("comment"));
	}

	public void setComment(String comment) {
		edit("comment", comment);
	}

	@Property(viewable = true, order = 6, name = "Created by")
	public String getCreatedBy() {
		return text("created_by");
	}

	@Override
	public boolean isEditable() {
		return true;
	}

	@Override
	public Set<String> functionsFor(Action action) {
		return switch (action) {
			case CREATE -> Set.of("add_schema_alias", "expand_schema");
			case EDIT -> Set.of("alter_schema_alias", "comment");
			case DELETE -> Set.of("drop_schema_alias");
		};
	}

	@Override
	public String catalogOf() {
		return catalog();
	}

	@Override
	protected String selfSql() {
		return PlatformSql.view("schemas", "catalog", catalog(), "path", getName());
	}

	@Override
	protected String stored(DBRProgressMonitor monitor, String id) {
		return switch (id) {
			case "physPath" -> text("phys_path");
			case "comment" -> text("comment");
			default -> null;
		};
	}

	private String target() {
		return AclGrammar.inCatalog(catalog(), getName());
	}

	@Override
	public List<String> createStatements() throws DBException {
		String comment = getComment();
		return List.of("CREATE VIRTUAL SCHEMA " + target() + (isExpansion() ? " FROM " : " AS ")
		               + AclGrammar.path(required(getPhysPath(), "the physical schema (Phys path)"))
		               + (AclGrammar.isBlank(comment) ? "" : " COMMENT " + AclGrammar.literal(comment)));
	}

	@Override
	public List<String> alterStatements(DBRProgressMonitor monitor, Set<String> changed) {
		List<String> out = new ArrayList<>();
		if (changed.contains("physPath") && !isExpansion()) {
			out.add("ALTER VIRTUAL SCHEMA " + target() + " SET PHYS " + AclGrammar.path(getPhysPath().trim()));
		}
		if (changed.contains("comment")) {
			out.add("COMMENT ON VIRTUAL SCHEMA " + target() + " IS " + AclGrammar.literal(getComment()));
		}
		return out;
	}

	@Override
	public List<String> dropStatements() {
		return List.of("DROP VIRTUAL SCHEMA " + target());
	}

	/** An expansion read again from its source (`PRUNE`: its records whose object is gone removed too). */
	public String refreshStatement(boolean prune) {
		return "ALTER VIRTUAL SCHEMA " + target() + " REFRESH" + (prune ? " PRUNE" : "");
	}

	@Override
	public List<String> operation(String kind) {
		return "refresh".equals(kind) && isExpansion() ? List.of(refreshStatement(false), refreshStatement(true)) : List.of();
	}

	@Override
	public String operationTitle(String kind) {
		return "Refresh from the source";
	}

	@Override
	public String operationExplanation(String kind) {
		return "Read the expansion's source again: a record for each object that appeared. PRUNE also removes the records "
		       + "whose object is gone (only the ones the expansion made).";
	}

	@Override
	public String operationFunction(String kind) {
		return "refresh_schema";
	}

	public static class Kinds implements IPropertyValueListProvider<AclVirtualSchema> {
		@Override
		public boolean allowCustomValue() {
			return false;
		}

		@Override
		public Object[] getPossibleValues(AclVirtualSchema object) {
			return new Object[] {"alias", "expansion"};
		}
	}
}
