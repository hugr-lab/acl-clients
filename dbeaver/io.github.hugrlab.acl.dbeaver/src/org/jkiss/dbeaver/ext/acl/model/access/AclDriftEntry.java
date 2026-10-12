package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.List;
import java.util.Map;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/** Where the node differs from its profile (platform.drift, spec 118.2). One property per column of its view, the parent's key aside (spec 007.3). */
public class AclDriftEntry extends AclPlatformObject implements Operable {
	public AclDriftEntry(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
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

	@Property(viewable = true, order = 2, name = "Kind")
	public String getKind() {
		return text("kind");
	}

	@Property(viewable = true, order = 3, name = "State")
	public String getState() {
		return text("state");
	}

	@Property(viewable = true, order = 4, name = "Node value")
	public String getNodeValue() {
		return text("node_value");
	}

	@Property(viewable = true, order = 5, name = "Profile value")
	public String getProfileValue() {
		return text("profile_value");
	}

	@Property(viewable = true, order = 6, name = "Scope")
	public String getScope() {
		return text("scope");
	}

	/** A value as CLUSTER SET writes it: a number bare, anything else a literal. */
	static String value(String text) {
		return text != null && text.trim().matches("-?[0-9]+(\\.[0-9]+)?") ? text.trim() : AclGrammar.literal(text == null ? "" : text);
	}

	/**
	 * Into the cluster profile (spec 008.3): what this node has and the profile lacks (or holds otherwise), as the
	 * statement that would make it the profile's - prefilled with what the node knows, `<…>` for what it does not
	 * (a source's type and secret, an extension's version and repository - never a credential).
	 */
	@Override
	public List<String> operation(String kind) {
		String state = text("state");
		if (!"into_profile".equals(kind) || !("node_only".equals(state) || "differs".equals(state))) {
			return List.of();
		}
		String name = AclGrammar.ident(getName());
		return switch (String.valueOf(text("kind"))) {
			case "setting" -> List.of("CLUSTER SET " + name + " = " + value(text("node_value")));
			case "database" -> List.of("CLUSTER ATTACH " + AclGrammar.literal(text("node_value")) + " AS " + name
			                           + " (TYPE <type>, SECRET <secret>)",
			                           "CLUSTER ATTACH " + AclGrammar.literal(text("node_value")) + " AS " + name + " (TYPE <type>)");
			case "extension" -> List.of("CLUSTER INSTALL EXTENSION " + name + " VERSION '<version>' FROM <repository>");
			default -> List.of();
		};
	}

	@Override
	public String operationTitle(String kind) {
		return "Add to the cluster profile";
	}

	@Override
	public String operationExplanation(String kind) {
		return "What this node has (" + text("kind") + " " + getName() + ", " + text("state") + ") written into the cluster "
		       + "profile - fill in what is in <…>: a postgres, mysql or mssql source names its SECRET, never a password.";
	}

	@Override
	public String operationFunction(String kind) {
		return switch (String.valueOf(text("kind"))) {
			case "setting" -> "cluster_setting";
			case "database" -> "cluster_attach";
			default -> "cluster_extension";
		};
	}
}
