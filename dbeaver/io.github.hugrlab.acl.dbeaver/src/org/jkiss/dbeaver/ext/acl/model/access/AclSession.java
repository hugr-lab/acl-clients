package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.List;
import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** A live session of the node (platform.sessions - never a handle). One property per column of its view, the parent's key aside (spec 007.3). */
public class AclSession extends AclPlatformObject implements Operable {
	public AclSession(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "id";
	}

	@Override
	@Property(viewable = true, order = 1)
	public String getName() {
		return super.getName();
	}

	@Property(viewable = true, order = 2, name = "Subject")
	public String getSubject() {
		return text("subject");
	}

	@Property(viewable = true, order = 3, name = "Roles")
	public String getRoles() {
		return text("roles");
	}

	@Property(viewable = true, order = 4, name = "Door")
	public String getDoor() {
		return text("door");
	}

	@Property(viewable = true, order = 5, name = "Idle seconds")
	public String getIdleSeconds() {
		return text("idle_seconds");
	}

	@Property(viewable = true, order = 6, name = "Expires at")
	public String getExpiresAt() {
		return text("expires_at");
	}

	@Property(viewable = true, order = 7, name = "Audit level")
	public String getAuditLevel() {
		return text("audit_level");
	}

	@Property(viewable = true, order = 8, name = "Profile level")
	public String getProfileLevel() {
		return text("profile_level");
	}

	@Property(viewable = true, order = 9, name = "Groups")
	public String getGroups() {
		return text("groups");
	}

	@Property(viewable = true, order = 10, name = "Charged group")
	public String getChargedGroup() {
		return text("charged_group");
	}

	@Property(viewable = true, order = 50, name = "Audit level source")
	public String getAuditLevelSource() {
		return text("audit_level_source");
	}

	@Property(viewable = true, order = 51, name = "Profile source")
	public String getProfileSource() {
		return text("profile_source");
	}

	private String id() {
		return AclGrammar.literal(getName());
	}

	@Override
	public List<String> operation(String kind) {
		return switch (kind) {
			case "kill" -> List.of("KILL SESSION " + id());
			case "audit" -> java.util.Arrays.stream(new String[] {"ALL", "DECISIONS", "DENIED", "OFF", "DEFAULT"})
			    .map(level -> "SET SESSION " + id() + " AUDIT LEVEL " + level).toList();
			case "profile" -> java.util.Arrays.stream(new String[] {"ON", "SAMPLED", "OFF"})
			    .map(level -> "PROFILE SESSION " + id() + " " + level).toList();
			default -> List.of();
		};
	}

	@Override
	public String operationTitle(String kind) {
		return switch (kind) {
			case "kill" -> "Kill session";
			case "audit" -> "Session audit level";
			default -> "Session profile level";
		};
	}

	@Override
	public String operationExplanation(String kind) {
		return switch (kind) {
			case "kill" -> "End session " + getName() + " (" + getSubject() + "): its client is disconnected and must sign in "
			               + "again.";
			case "audit" -> "The operator's audit level on session " + getName() + " - DEFAULT clears it back to the "
			                + "policy's and the node's.";
			default -> "What the session's statements' execution profiles are recorded at, from its next statement on - "
			           + "OFF clears the override.";
		};
	}

	@Override
	public String operationFunction(String kind) {
		return switch (kind) {
			case "kill" -> "kill_session";
			case "audit" -> "session_audit_level";
			default -> "session_profile";
		};
	}
}
