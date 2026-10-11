package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.List;
import java.util.Map;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A virtual catalog (platform.catalogs). One property per column of its view, the parent's key aside (spec 007.3). */
public class AclVirtualCatalog extends AclPlatformObject {
	public AclVirtualCatalog(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "catalog";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return super.getName();
	}

	@Property(viewable = true, order = 2, name = "Comment")
	public String getComment() {
		return text("comment");
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
