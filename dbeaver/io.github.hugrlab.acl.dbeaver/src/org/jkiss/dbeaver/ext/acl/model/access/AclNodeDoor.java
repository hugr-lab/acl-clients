package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A quack door and its seats (platform.node_doors, spec 079). One property per column of its view, the parent's key aside (spec 007.3). */
public class AclNodeDoor extends AclPlatformObject {
	public AclNodeDoor(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "uri";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return super.getName();
	}

	@Property(viewable = true, order = 2, name = "Door")
	public String getDoor() {
		return text("door");
	}

	@Property(viewable = true, order = 3, name = "Seats")
	public String getSeats() {
		return text("seats");
	}

	@Property(viewable = true, order = 4, name = "Seated")
	public String getSeated() {
		return text("seated");
	}

	@Property(viewable = true, order = 5, name = "Seats left")
	public String getSeatsLeft() {
		return text("seats_left");
	}
}
