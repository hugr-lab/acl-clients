package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.List;
import java.util.Map;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A virtual table or view (platform.relations). One property per column of its view, the parent's key aside (spec 007.3). */
public class AclRelation extends AclPlatformObject {
	public AclRelation(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "name";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return super.getName();
	}

	@Property(viewable = true, order = 2, name = "Form")
	public String getForm() {
		return text("form");
	}

	@Property(viewable = true, order = 3, name = "Phys")
	public String getPhys() {
		return text("phys");
	}

	@Property(viewable = true, order = 4, name = "Rls")
	public String getRls() {
		return text("rls");
	}

	@Property(viewable = true, order = 5, name = "Origin")
	public String getOrigin() {
		return text("origin");
	}

	@Property(viewable = true, order = 6, name = "Comment")
	public String getComment() {
		return text("comment");
	}

	@Property(viewable = true, order = 7, name = "Created by")
	public String getCreatedBy() {
		return text("created_by");
	}

	@Association
	public List<AclRelationColumn> getColumns(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "Columns", PlatformSql.view("relation_columns", "catalog", text("catalog"), "relation", getName()), r -> new AclRelationColumn(dataSource, this, r));
	}

	@Association
	public List<AclKeyColumn> getKeyColumns(DBRProgressMonitor monitor) throws DBException {
		return children(monitor, "KeyColumns", PlatformSql.view("keys", "catalog", text("catalog"), "object", getName()), r -> new AclKeyColumn(dataSource, this, r));
	}

	@Property(viewable = true, order = 50, name = "View sql")
	public String getViewSql() {
		return text("view_sql");
	}

	@Property(viewable = true, order = 51, name = "Rls checked")
	public String getRlsChecked() {
		return text("rls_checked");
	}

	@Property(viewable = true, order = 52, name = "Alias types")
	public String getAliasTypes() {
		return text("alias_types");
	}

	@Property(viewable = true, order = 53, name = "Enum types")
	public String getEnumTypes() {
		return text("enum_types");
	}
}
