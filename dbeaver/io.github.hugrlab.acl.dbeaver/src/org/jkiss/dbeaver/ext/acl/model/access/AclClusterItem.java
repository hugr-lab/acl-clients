package org.jkiss.dbeaver.ext.acl.model.access;

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
 * An item of the cluster profile (platform.cluster_items, duckdb-acl specs 093 / 096 / 118). Read: one property per
 * column of its view (spec 007.3). Written (spec 008.3), a cluster node's and the cluster bundle's: a setting `CLUSTER
 * SET name = value [IN GROUP g]` (a change is the SET again, the drop `RESET`); a source `CLUSTER ATTACH '<path>' AS
 * alias (TYPE t[, SECRET s][, option value …]) [LINEAGE '…'] [DEPENDS ON (…)] [IN GROUP g] [COMMENT …]` (a change is
 * the ATTACH again - a re-point -, the drop `DETACH`); an extension `CLUSTER INSTALL EXTENSION n VERSION 'v' [FROM
 * repo] [IN GROUP g] [COMMENT …]` (a change of version `UPDATE`, the drop `REMOVE`). No credential: a source names its
 * secret.
 */
public class AclClusterItem extends AclPlatformObject {
	public AclClusterItem(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "name";
	}

	private String spec(String key) {
		Object value = PlatformValues.fieldsOf(row.get("spec")).get(key);
		return value == null ? null : value.toString();
	}

	@Override
	@Property(viewable = true, editable = true, order = 1, description = "A setting's name, a source's alias, an extension's name")
	public String getName() {
		return super.getName();
	}

	@Property(viewable = true, editable = true, order = 2, name = "Kind", listProvider = Kinds.class)
	public String getKind() {
		return edited("kind", text("kind") == null ? "setting" : text("kind"));
	}

	public void setKind(String value) {
		edit("kind", value);
	}

	@Property(viewable = true, editable = true, order = 3, name = "Group",
	          description = "The resource group whose nodes it applies to; empty = the whole cluster")
	public String getScope() {
		return edited("scope", text("scope"));
	}

	public void setScope(String value) {
		edit("scope", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 4, name = "Value", description = "A setting's value")
	public String getValue() {
		return edited("value", spec("value"));
	}

	public void setValue(String value) {
		edit("value", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 5, name = "Path", description = "A source's path / connection string - no credential in it")
	public String getPath() {
		return edited("path", spec("path"));
	}

	public void setPath(String value) {
		edit("path", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 6, name = "Type", description = "A source's type: duckdb, postgres, …")
	public String getType() {
		return edited("type", spec("type"));
	}

	public void setType(String value) {
		edit("type", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 7, name = "Secret",
	          description = "A source's secret, by name (a postgres, mysql or mssql source must name one)")
	public String getSecret() {
		String secret = spec("secret");
		return edited("secret", secret == null || secret.isEmpty() ? null : secret);
	}

	public void setSecret(String value) {
		edit("secret", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 8, name = "Options",
	          description = "A source's other ATTACH options: READ_ONLY, SCHEMA 'x'")
	public String getOptions() {
		return edited("options", options(spec("options")));
	}

	public void setOptions(String value) {
		edit("options", value);
	}

	/** The stored options (JSON) as ATTACH writes them. */
	static String options(String json) {
		if (json == null || json.isBlank() || json.trim().equals("{}")) {
			return "";
		}
		String inner = json.trim().replaceAll("^\\{|\\}$", "");
		List<String> out = new ArrayList<>();
		for (String pair : GrantColumns.split(inner, ',')) {
			String[] kv = pair.split(":", 2);
			String key = kv[0].trim().replaceAll("^\"|\"$", "");
			String value = kv.length > 1 ? kv[1].trim() : "";
			out.add(value.equals("true") ? key : value.startsWith("\"") ? key + " " + AclGrammar.literal(value.replaceAll("^\"|\"$", "")) : key + " " + value);
		}
		return String.join(", ", out);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 9, name = "Lineage",
	          description = "The source's identity in lineage: postgres://host:5432/db - never a credential")
	public String getLineage() {
		return edited("lineage", spec("lineage"));
	}

	public void setLineage(String value) {
		edit("lineage", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 10, name = "Depends on",
	          description = "The sources this one is attached after: a, b")
	public String getDependsOn() {
		return edited("dependsOn", PlatformValues.bareList(row.get("depends_on")));
	}

	public void setDependsOn(String value) {
		edit("dependsOn", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 11, name = "Version", description = "An extension's version")
	public String getVersion() {
		return edited("version", spec("version"));
	}

	public void setVersion(String value) {
		edit("version", value);
	}

	@Property(viewable = true, editable = true, order = 12, name = "Repository",
	          description = "The trusted extension repository it comes from (with exactly one, may be left out)")
	public String getRepository() {
		return edited("repository", spec("repository"));
	}

	public void setRepository(String value) {
		edit("repository", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 13, name = "Comment")
	public String getComment() {
		return edited("comment", text("comment"));
	}

	public void setComment(String value) {
		edit("comment", value);
	}

	@Property(viewable = true, order = 14, name = "Class", description = "hot (applied at once), drain or restart")
	public String getItemClass() {
		return text("class");
	}

	@Property(viewable = true, order = 15, name = "Profile version")
	public String getProfileVersion() {
		return text("version");
	}

	@Override
	public boolean isEditable() {
		return true;
	}

	@Override
	public Set<String> functionsFor(Action action) {
		return switch (getKind()) {
			case "source" -> action == Action.DELETE ? Set.of("cluster_detach") : Set.of("cluster_attach");
			case "extension" -> Set.of("cluster_extension");
			default -> Set.of("cluster_setting");
		};
	}

	@Override
	protected String selfSql() {
		return PlatformSql.view("cluster_items", "name", getName(), "kind", text("kind") == null ? getKind() : text("kind"));
	}

	@Override
	protected String stored(DBRProgressMonitor monitor, String id) {
		return switch (id) {
			case "value" -> spec("value");
			case "path" -> spec("path");
			case "type" -> spec("type");
			case "version" -> spec("version");
			case "comment" -> text("comment");
			default -> null;
		};
	}

	private String inGroup() {
		return AclGrammar.isBlank(getScope()) ? "" : " IN GROUP " + AclGrammar.ident(getScope().trim());
	}

	private String comment() {
		return AclGrammar.isBlank(getComment()) ? "" : " COMMENT " + AclGrammar.literal(getComment());
	}

	private String attach() {
		StringBuilder options = new StringBuilder("TYPE ").append(getType() == null ? "<type>" : getType().trim());
		if (!AclGrammar.isBlank(getSecret())) {
			options.append(", SECRET ").append(AclGrammar.ident(getSecret().trim()));
		}
		if (!AclGrammar.isBlank(getOptions())) {
			options.append(", ").append(getOptions().trim());
		}
		return "CLUSTER ATTACH " + AclGrammar.literal(getPath() == null ? "" : getPath()) + " AS " + AclGrammar.ident(getName())
		       + " (" + options + ")"
		       + (AclGrammar.isBlank(getLineage()) ? "" : " LINEAGE " + AclGrammar.literal(getLineage().trim()))
		       + (AclGrammar.isBlank(getDependsOn()) ? "" : " DEPENDS ON (" + getDependsOn().trim() + ")") + inGroup() + comment();
	}

	@Override
	public List<String> createStatements() {
		return switch (getKind()) {
			case "source" -> List.of(attach());
			case "extension" -> List.of("CLUSTER INSTALL EXTENSION " + AclGrammar.ident(getName()) + " VERSION "
			                            + AclGrammar.literal(getVersion() == null ? "" : getVersion().trim())
			                            + (AclGrammar.isBlank(getRepository()) ? "" : " FROM " + AclGrammar.ident(getRepository().trim()))
			                            + inGroup() + comment());
			default -> List.of("CLUSTER SET " + AclGrammar.ident(getName()) + " = " + AclDriftEntry.value(getValue()) + inGroup());
		};
	}

	@Override
	public List<String> alterStatements(DBRProgressMonitor monitor, Set<String> changed) {
		if (changed.isEmpty()) {
			return List.of();
		}
		return switch (getKind()) {
			case "source" -> List.of(attach()); // a re-point: the ATTACH again
			case "extension" -> changed.contains("version")
			                        ? List.of("CLUSTER UPDATE EXTENSION " + AclGrammar.ident(getName()) + " VERSION "
			                                  + AclGrammar.literal(getVersion().trim()) + inGroup())
			                        : List.of();
			default -> changed.contains("value") ? createStatements() : List.of();
		};
	}

	@Override
	public List<String> dropStatements() {
		return switch (getKind()) {
			case "source" -> List.of("CLUSTER DETACH " + AclGrammar.ident(getName()) + inGroup());
			case "extension" -> List.of("CLUSTER REMOVE EXTENSION " + AclGrammar.ident(getName()) + inGroup());
			default -> List.of("CLUSTER RESET " + AclGrammar.ident(getName()) + inGroup());
		};
	}

	public static class Kinds implements IPropertyValueListProvider<AclClusterItem> {
		@Override
		public boolean allowCustomValue() {
			return false;
		}

		@Override
		public Object[] getPossibleValues(AclClusterItem object) {
			return new Object[] {"setting", "source", "extension"};
		}
	}
}
