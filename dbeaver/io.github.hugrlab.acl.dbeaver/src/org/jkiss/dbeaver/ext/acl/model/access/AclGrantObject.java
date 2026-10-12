package org.jkiss.dbeaver.ext.acl.model.access;

import java.sql.Struct;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * A role's grant - on a catalog, a schema or an object (spec 008.1): what the three share is the role (the parent's)
 * and the capabilities, shown as boxes - **Unstated** (the grant names none: every data capability for a catalog grant,
 * the catalog grant's for a schema or object grant, duckdb-acl spec 012) and one per capability. Ticking a capability
 * makes the grant state its own list; ticking Unstated clears it.
 */
public abstract class AclGrantObject extends AclPlatformObject {
	protected AclGrantObject(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		super(dataSource, parent, row);
	}

	/** The role the grant is to: the row's, or - a new grant - the role it is made under. */
	public String role() {
		String role = text("role");
		if (role == null && getParentObject() instanceof AclRole parentRole) {
			return parentRole.getName();
		}
		return role;
	}

	/** The capabilities the grant states as the node holds them; null = unstated. */
	protected Set<String> storedCaps() {
		Object caps = row.get("caps");
		if (caps == null) {
			return null;
		}
		Set<String> held = new LinkedHashSet<>();
		Map<?, ?> fields = caps instanceof Struct struct ? PlatformValues.fields(struct)
		                   : caps instanceof Map<?, ?> map    ? map
		                                                       : null;
		if (fields != null) {
			for (Map.Entry<?, ?> entry : fields.entrySet()) {
				if (Boolean.TRUE.equals(entry.getValue())) {
					held.add(String.valueOf(entry.getKey()));
				}
			}
		}
		return held;
	}

	/** The capabilities as they will be written: the user's, else the node's; null = unstated. */
	@SuppressWarnings("unchecked")
	public Set<String> caps() {
		return edited("caps", storedCaps());
	}

	protected void setCap(String cap, boolean held) {
		Set<String> caps = caps() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(caps());
		if (held) {
			caps.add(cap);
		} else {
			caps.remove(cap);
		}
		edit("caps", caps);
	}

	private boolean has(String cap) {
		Set<String> caps = caps();
		return caps != null && caps.contains(cap);
	}

	/** The caps as text, for the stale check and the read-only column. */
	protected String capsText(Set<String> caps) {
		return caps == null ? "unstated" : String.join(", ", AclGrammar.CAPABILITIES.stream().filter(caps::contains).toList());
	}

	@Property(viewable = false, editable = true, updatable = true, order = 20, category = "Capabilities", name = "Unstated",
	          description = "The grant names no capability: every data capability for a catalog grant, the catalog "
	                        + "grant's for a schema or object grant")
	public boolean isCapsUnstated() {
		return caps() == null;
	}

	public void setCapsUnstated(boolean unstated) {
		edit("caps", unstated ? null : new LinkedHashSet<String>());
	}

	@Property(viewable = false, editable = true, updatable = true, order = 21, category = "Capabilities", name = "select")
	public boolean isCapSelect() {
		return has("select");
	}

	public void setCapSelect(boolean held) {
		setCap("select", held);
	}

	@Property(viewable = false, editable = true, updatable = true, order = 22, category = "Capabilities", name = "insert")
	public boolean isCapInsert() {
		return has("insert");
	}

	public void setCapInsert(boolean held) {
		setCap("insert", held);
	}

	@Property(viewable = false, editable = true, updatable = true, order = 23, category = "Capabilities", name = "update")
	public boolean isCapUpdate() {
		return has("update");
	}

	public void setCapUpdate(boolean held) {
		setCap("update", held);
	}

	@Property(viewable = false, editable = true, updatable = true, order = 24, category = "Capabilities", name = "delete")
	public boolean isCapDelete() {
		return has("delete");
	}

	public void setCapDelete(boolean held) {
		setCap("delete", held);
	}

	@Property(viewable = false, editable = true, updatable = true, order = 25, category = "Capabilities", name = "merge")
	public boolean isCapMerge() {
		return has("merge");
	}

	public void setCapMerge(boolean held) {
		setCap("merge", held);
	}

	@Property(viewable = false, editable = true, updatable = true, order = 26, category = "Capabilities", name = "create",
	          description = "Create objects in it (a schema grant)")
	public boolean isCapCreate() {
		return has("create");
	}

	public void setCapCreate(boolean held) {
		setCap("create", held);
	}

	@Property(viewable = false, editable = true, updatable = true, order = 27, category = "Capabilities", name = "drop",
	          description = "Drop objects in it (a schema grant)")
	public boolean isCapDrop() {
		return has("drop");
	}

	public void setCapDrop(boolean held) {
		setCap("drop", held);
	}

	@Property(viewable = false, editable = true, updatable = true, order = 28, category = "Capabilities", name = "temp",
	          description = "Session temp tables on the Flight door (the MAIN catalog grant)")
	public boolean isCapTemp() {
		return has("temp");
	}

	public void setCapTemp(boolean held) {
		setCap("temp", held);
	}

	@Property(viewable = false, editable = true, updatable = true, order = 29, category = "Capabilities", name = "explain",
	          description = "EXPLAIN (the MAIN catalog grant): a plan names physical objects")
	public boolean isCapExplain() {
		return has("explain");
	}

	public void setCapExplain(boolean held) {
		setCap("explain", held);
	}

	@Property(viewable = false, editable = true, updatable = true, order = 30, category = "Capabilities", name = "secrets",
	          description = "The node's secrets service (the MAIN catalog grant)")
	public boolean isCapSecrets() {
		return has("secrets");
	}

	public void setCapSecrets(boolean held) {
		setCap("secrets", held);
	}

	@Property(viewable = false, editable = true, updatable = true, order = 31, category = "Capabilities", name = "manage",
	          description = "Administer this catalog (a catalog grant)")
	public boolean isCapManage() {
		return has("manage");
	}

	public void setCapManage(boolean held) {
		setCap("manage", held);
	}

	/** Whether a property id is one of the capability boxes. */
	protected static boolean isCapsProperty(String id) {
		return id.equals("capsUnstated") || id.startsWith("cap");
	}

	protected static boolean anyCaps(Set<String> changed) {
		return changed.stream().anyMatch(AclGrantObject::isCapsProperty);
	}
}
