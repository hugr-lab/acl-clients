package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** What a role reads of one object, and why (platform.effective_rights, spec 120.2). One property per column of its view, the parent's key aside (spec 007.3). */
public class AclEffectiveRight extends AclPlatformObject {
	public AclEffectiveRight(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "object";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return super.getName();
	}

	@Property(viewable = true, order = 2, name = "Kind")
	public String getKind() {
		return text("kind");
	}

	@Property(viewable = true, order = 3, name = "Decided by")
	public String getDecidedBy() {
		return text("decided_by");
	}

	@Property(viewable = true, order = 4, name = "Caps")
	public String getCaps() {
		return text("caps");
	}

	@Property(viewable = true, order = 5, name = "Main")
	public String getMain() {
		return text("main");
	}

	@Property(viewable = true, order = 6, name = "Rls")
	public String getRls() {
		return text("rls");
	}

	@Property(viewable = true, order = 7, name = "Columns")
	public String getColumns() {
		return text("columns");
	}

	@Property(viewable = true, order = 8, name = "Masks")
	public String getMasks() {
		return text("masks");
	}

	@Property(viewable = true, order = 9, name = "Writable")
	public String getWritable() {
		return text("writable");
	}

	@Property(viewable = true, order = 10, name = "Refused")
	public String getRefused() {
		return text("refused");
	}
}
