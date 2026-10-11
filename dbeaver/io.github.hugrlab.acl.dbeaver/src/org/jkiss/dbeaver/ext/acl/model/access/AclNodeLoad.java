package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** The node's load report (platform.node_load, spec 079). One property per column of its view, the parent's key aside (spec 007.3). */
public class AclNodeLoad extends AclPlatformObject {
	public AclNodeLoad(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "group";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return text("group") == null ? "node" : "node (" + text("group") + ")";
	}

	@Property(viewable = true, order = 2, name = "Draining")
	public String getDraining() {
		return text("draining");
	}

	@Property(viewable = true, order = 3, name = "Sessions live")
	public String getSessionsLive() {
		return text("sessions_live");
	}

	@Property(viewable = true, order = 4, name = "Sessions max")
	public String getSessionsMax() {
		return text("sessions_max");
	}

	@Property(viewable = true, order = 5, name = "Sessions by door")
	public String getSessionsByDoor() {
		return text("sessions_by_door");
	}

	@Property(viewable = true, order = 6, name = "Sessions by group")
	public String getSessionsByGroup() {
		return text("sessions_by_group");
	}

	@Property(viewable = true, order = 7, name = "Admit new session")
	public String getAdmitNewSession() {
		return text("admit_new_session");
	}

	@Property(viewable = true, order = 8, name = "Admit new quack client")
	public String getAdmitNewQuackClient() {
		return text("admit_new_quack_client");
	}

	@Property(viewable = true, order = 9, name = "Admit new stream")
	public String getAdmitNewStream() {
		return text("admit_new_stream");
	}

	@Property(viewable = true, order = 10, name = "Config target")
	public String getConfigTarget() {
		return text("config_target");
	}

	@Property(viewable = true, order = 11, name = "Config applied")
	public String getConfigApplied() {
		return text("config_applied");
	}

	@Property(viewable = true, order = 50, name = "Observe")
	public String getObserve() {
		return text("observe");
	}

	@Property(viewable = true, order = 51, name = "Group known")
	public String getGroupKnown() {
		return text("group_known");
	}
}
