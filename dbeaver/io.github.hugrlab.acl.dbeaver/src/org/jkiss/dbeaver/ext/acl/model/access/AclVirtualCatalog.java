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
 * A virtual catalog (platform.catalogs). Read: one property per column of its view (spec 007.3). Written (spec 008.1):
 * `CREATE VIRTUAL CATALOG c [COMMENT '…']`, `ALTER VIRTUAL CATALOG c SET COMMENT '…'`, `DROP VIRTUAL CATALOG c` - without
 * CASCADE: a catalog some role holds a grant on is refused by the node, which names the roles.
 */
public class AclVirtualCatalog extends AclPlatformObject {
	public AclVirtualCatalog(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "catalog";
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

	@Override
	public boolean isEditable() {
		return true;
	}

	@Override
	protected String selfSql() {
		return PlatformSql.view("catalogs", "catalog", getName());
	}

	@Override
	protected String stored(DBRProgressMonitor monitor, String id) {
		return "comment".equals(id) ? text("comment") : null;
	}

	@Override
	public List<String> createStatements() {
		String comment = getComment();
		return List.of("CREATE VIRTUAL CATALOG " + AclGrammar.path(getName())
		               + (AclGrammar.isBlank(comment) ? "" : " COMMENT " + AclGrammar.literal(comment)));
	}

	@Override
	public List<String> alterStatements(DBRProgressMonitor monitor, Set<String> changed) {
		List<String> out = new ArrayList<>();
		if (changed.contains("comment")) {
			out.add("ALTER VIRTUAL CATALOG " + AclGrammar.path(getName()) + " SET COMMENT " + AclGrammar.literal(getComment()));
		}
		return out;
	}

	@Override
	public List<String> dropStatements() {
		return List.of("DROP VIRTUAL CATALOG " + AclGrammar.path(getName()));
	}

	@Association
	public List<AclRelation> getRelations(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "Relations", PlatformSql.view("relations", "catalog", getName()), r -> new AclRelation(dataSource, this, r));
	}

	@Association
	public List<AclVirtualSchema> getVirtualSchemas(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "VirtualSchemas", PlatformSql.view("schemas", "catalog", getName()), r -> new AclVirtualSchema(dataSource, this, r));
	}

	@Association
	public List<AclFunctionEntry> getFunctionEntries(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "FunctionEntries", PlatformSql.view("functions", "catalog", getName()), r -> new AclFunctionEntry(dataSource, this, r));
	}

	@Association
	public List<AclReferenceEntry> getReferenceEntries(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "ReferenceEntries", PlatformSql.view("references", "catalog", getName()), r -> new AclReferenceEntry(dataSource, this, r));
	}

	@Association
	public List<AclFinding> getFindings(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "Findings", PlatformSql.call("check_catalog", getName()), r -> new AclFinding(dataSource, this, r));
	}
}
