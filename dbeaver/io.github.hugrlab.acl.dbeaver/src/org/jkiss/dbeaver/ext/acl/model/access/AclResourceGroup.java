package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * A resource group (platform.resource_groups, duckdb-acl specs 085 / 096). Read: one property per column of its view
 * (spec 007.3). Written (spec 008.2): `CREATE RESOURCE GROUP g (limit value, …) [DEFAULT] [COMMENT …]`; `ALTER RESOURCE
 * GROUP g SET (limit value, …)` - the limits changed only (spec 120), `SET DEFAULT | DROP DEFAULT`, `SET COMMENT`;
 * `DROP RESOURCE GROUP g`. A limit is set or changed, never unset - the node refuses NULL (0 means "unlimited" where
 * the table says so).
 */
public class AclResourceGroup extends AclPlatformObject {
	static final List<String> LIMITS = List.of("window_start", "window_max", "batch_bytes", "max_result_rows",
	                                           "queue_priority", "max_sessions");

	public AclResourceGroup(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	@Override
	protected String nameKey() {
		return "group";
	}

	@Override
	@Property(viewable = true, editable = true, order = 1)
	public String getName() {
		return super.getName();
	}

	/** A limit's property id: window_start -> windowStart. */
	static String id(String column) {
		StringBuilder out = new StringBuilder();
		boolean up = false;
		for (char c : column.toCharArray()) {
			if (c == '_') {
				up = true;
			} else {
				out.append(up ? Character.toUpperCase(c) : c);
				up = false;
			}
		}
		return out.toString();
	}

	private String limit(String column) {
		return edited(id(column), text(column));
	}

	@Property(viewable = true, editable = true, updatable = true, order = 2, name = "Window start",
	          description = "The quack fetch window at a stream's start; 0 = no window")
	public String getWindowStart() {
		return limit("window_start");
	}

	public void setWindowStart(String value) {
		edit(id("window_start"), value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 3, name = "Window max",
	          description = "How far the window grows; 0 = no cap")
	public String getWindowMax() {
		return limit("window_max");
	}

	public void setWindowMax(String value) {
		edit(id("window_max"), value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 4, name = "Batch bytes",
	          description = "The quack batch target (bytes, or '32MiB')")
	public String getBatchBytes() {
		return limit("batch_bytes");
	}

	public void setBatchBytes(String value) {
		edit(id("batch_bytes"), value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 5, name = "Max result rows",
	          description = "Rows a Flight stream hands out before its refusal; 0 = unlimited")
	public String getMaxResultRows() {
		return limit("max_result_rows");
	}

	public void setMaxResultRows(String value) {
		edit(id("max_result_rows"), value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 6, name = "Queue priority",
	          description = "Higher goes first in the stream budget's line (may be negative)")
	public String getQueuePriority() {
		return limit("queue_priority");
	}

	public void setQueuePriority(String value) {
		edit(id("queue_priority"), value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 7, name = "Max sessions",
	          description = "Live sessions charged to the group; 0 = unlimited")
	public String getMaxSessions() {
		return limit("max_sessions");
	}

	public void setMaxSessions(String value) {
		edit(id("max_sessions"), value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 8, name = "Default",
	          description = "The group of the principals bound to none (at most one)",
	          hideExpr = "!object.dataSource.clusterNode()") // spec 125: a node-group concept (spec 096)
	public boolean isDefault() {
		return edited("default", Boolean.TRUE.equals(row.get("is_default")));
	}

	public void setDefault(boolean value) {
		edit("default", value);
	}

	@Property(viewable = true, editable = true, updatable = true, order = 9, name = "Comment")
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
	public Set<String> functionsFor(Action action) {
		return switch (action) {
			case CREATE -> Set.of("create_resource_group");
			case EDIT -> Set.of("alter_resource_group");
			case DELETE -> Set.of("drop_resource_group");
		};
	}

	@Override
	protected String selfSql() {
		return PlatformSql.view("resource_groups", "group", getName());
	}

	@Override
	protected String stored(DBRProgressMonitor monitor, String id) {
		for (String limit : LIMITS) {
			if (id(limit).equals(id)) {
				return text(limit);
			}
		}
		return switch (id) {
			case "default" -> String.valueOf(Boolean.TRUE.equals(row.get("is_default")));
			case "comment" -> text("comment");
			default -> null;
		};
	}

	private String limits(Set<String> only) throws DBException {
		List<String> items = new ArrayList<>();
		for (String limit : LIMITS) {
			if (only != null && !only.contains(limit)) {
				continue;
			}
			String value = limit(limit);
			if (AclGrammar.isBlank(value)) {
				if (only != null) {
					throw new DBException("duckdb-acl: a resource group's " + limit + " is set or changed, never unset "
					                      + "(the node's rule) - 0 means unlimited where it applies");
				}
				continue;
			}
			items.add(limit + " " + (value.trim().matches("-?[0-9]+") ? value.trim() : AclGrammar.literal(value.trim())));
		}
		return String.join(", ", items);
	}

	@Override
	public List<String> createStatements() throws DBException {
		String limits;
		try {
			limits = limits(null);
		} catch (DBException e) {
			limits = "";
		}
		return List.of("CREATE RESOURCE GROUP " + AclGrammar.ident(getName()) + " (" + limits + ")"
		               + (isDefault() ? " DEFAULT" : "")
		               + (AclGrammar.isBlank(getComment()) ? "" : " COMMENT " + AclGrammar.literal(getComment())));
	}

	@Override
	public List<String> alterStatements(DBRProgressMonitor monitor, Set<String> changed) throws DBException {
		String alter = "ALTER RESOURCE GROUP " + AclGrammar.ident(getName());
		List<String> out = new ArrayList<>();
		Set<String> limits = new java.util.LinkedHashSet<>();
		for (String limit : LIMITS) {
			if (changed.contains(id(limit))) {
				limits.add(limit);
			}
		}
		if (!limits.isEmpty()) {
			out.add(alter + " SET (" + limits(limits) + ")");
		}
		if (changed.contains("default")) {
			out.add(alter + (isDefault() ? " SET DEFAULT" : " DROP DEFAULT"));
		}
		if (changed.contains("comment")) {
			out.add(alter + " SET COMMENT " + AclGrammar.literal(getComment() == null ? "" : getComment()));
		}
		return out;
	}

	@Override
	public List<String> dropStatements() {
		return List.of("DROP RESOURCE GROUP " + AclGrammar.ident(getName()));
	}
}
