package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A resource group (platform.resource_groups, spec 085). Generated shape: one property per column (spec 007.3). */
public class AclResourceGroup extends AclPlatformObject {
	public AclResourceGroup(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "group";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return super.getName();
	}

	@Property(viewable = true, order = 2, name = "Window start")
	public String getWindowStart() {
		return text("window_start");
	}

	@Property(viewable = true, order = 3, name = "Window max")
	public String getWindowMax() {
		return text("window_max");
	}

	@Property(viewable = true, order = 4, name = "Batch bytes")
	public String getBatchBytes() {
		return text("batch_bytes");
	}

	@Property(viewable = true, order = 5, name = "Max result rows")
	public String getMaxResultRows() {
		return text("max_result_rows");
	}

	@Property(viewable = true, order = 6, name = "Queue priority")
	public String getQueuePriority() {
		return text("queue_priority");
	}

	@Property(viewable = true, order = 7, name = "Max sessions")
	public String getMaxSessions() {
		return text("max_sessions");
	}

	@Property(viewable = true, order = 8, name = "Is default")
	public String getIsDefault() {
		return text("is_default");
	}

	@Property(viewable = true, order = 9, name = "Comment")
	public String getComment() {
		return text("comment");
	}
}
