package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.DBPRefreshableObject;
import org.jkiss.dbeaver.model.DBPSaveableObject;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * A node of the Access control branch (spec 007.3): one row of a `platform` view. Its children are read when it is
 * expanded - one statement each - and kept until it is refreshed.
 *
 * An editable one (spec 008) also says how it is written in the management grammar - the statements that create it,
 * change the properties the user changed (one each, never the ones left alone) and drop it - and how its own row is
 * read again: after a save (the properties then show what the node holds) and before one (a property someone else
 * changed meanwhile is not overwritten unasked).
 */
public abstract class AclPlatformObject implements DBSObject, DBPRefreshableObject, DBPSaveableObject {
	protected final AclDataSource dataSource;
	private final DBSObject parent;
	protected final Map<String, Object> row;
	private final Map<String, List<?>> loaded = new HashMap<>();
	/** The user's values not saved yet, by property id; a property not here is what the node holds. */
	private final Map<String, Object> edits = new HashMap<>();
	private boolean persisted = true;
	private boolean creating;

	protected AclPlatformObject(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		this.dataSource = dataSource;
		this.parent = parent;
		this.row = new LinkedHashMap<>(row);
	}

	/** The column that names it. */
	protected abstract String nameKey();

	protected String text(String key) {
		return PlatformValues.text(row.get(key));
	}

	@NotNull
	@Override
	public String getName() {
		String name = edited("name", text(nameKey()));
		return name == null ? "" : name;
	}

	/** A new object's name (DBeaver's name field); a stored one keeps its name - the grammar renames nothing here. */
	public void setName(String name) {
		edit("name", name);
	}

	@Override
	public String getDescription() {
		return null;
	}

	@Override
	public DBSObject getParentObject() {
		return parent;
	}

	@Override
	public AclDataSource getDataSource() {
		return dataSource;
	}

	@Override
	public boolean isPersisted() {
		return persisted;
	}

	@Override
	public void setPersisted(boolean persisted) {
		this.persisted = persisted;
	}

	// --- edits -----------------------------------------------------------------------------------------------------

	/** A property's value: the user's unsaved one, else what the node holds. */
	@SuppressWarnings("unchecked")
	protected <T> T edited(String id, T stored) {
		return edits.containsKey(id) ? (T) edits.get(id) : stored;
	}

	/** A value the node holds only behind a read (a child view): read when the user has not edited it. */
	public interface StoredRead<T> {
		T read() throws DBException;
	}

	@SuppressWarnings("unchecked")
	protected <T> T editedOr(String id, StoredRead<T> stored) throws DBException {
		return edits.containsKey(id) ? (T) edits.get(id) : stored.read();
	}

	protected void edit(String id, Object value) {
		edits.put(id, value);
	}

	/** Made by Create and not saved yet: its properties go into its create statement, never into an ALTER. */
	public boolean isCreating() {
		return creating;
	}

	public void markCreating() {
		this.creating = true;
		this.persisted = false;
	}

	/** Whether it can be written at all: a class of the branch that is only read answers false. */
	public boolean isEditable() {
		return false;
	}

	/** The statements that create it, in order. */
	public List<String> createStatements() {
		return List.of();
	}

	/** The statements for the properties the user changed (property ids), one per property, in the class's order. */
	public List<String> alterStatements(DBRProgressMonitor monitor, Set<String> changed) throws DBException {
		return List.of();
	}

	/** The statements that drop it. */
	public List<String> dropStatements() {
		return List.of();
	}

	/** The statement that reads its own row again (by its key), or null. */
	@Nullable
	protected String selfSql() {
		return null;
	}

	/**
	 * A property's value as the node holds it - the user's edit aside: the one the stale check compares. A property
	 * a class reads from a child view (a role's claims, a relation's columns) reads it here.
	 */
	@Nullable
	protected String stored(DBRProgressMonitor monitor, String id) throws DBException {
		return null;
	}

	/**
	 * Before a save of a stored object: the properties the user changed whose value on the node is no longer the one
	 * the form was opened with (property id - "was", "now"), or that it is gone (the key {@code ""}).
	 */
	public Map<String, String[]> changedOnTheNode(DBRProgressMonitor monitor, Set<String> changed) throws DBException {
		Map<String, String[]> out = new LinkedHashMap<>();
		String sql = selfSql();
		if (sql == null || !persisted) {
			return out;
		}
		List<Map<String, Object>> rows = dataSource.platformRows(monitor, sql);
		if (rows.isEmpty()) {
			out.put("", new String[] {getName(), null});
			return out;
		}
		AclPlatformObject now = sameKind(rows.get(0));
		for (String id : changed) {
			String was = stored(monitor, id);
			String is = now.stored(monitor, id);
			if (!Objects.equals(was, is)) {
				out.put(id, new String[] {was, is});
			}
		}
		return out;
	}

	/** An object of this class over another row (the constructor every class of the branch has). */
	private AclPlatformObject sameKind(Map<String, Object> other) throws DBException {
		try {
			return getClass().getConstructor(AclDataSource.class, DBSObject.class, Map.class)
			    .newInstance(dataSource, parent, other);
		} catch (ReflectiveOperationException e) {
			throw new DBException("duckdb-acl: " + getClass().getSimpleName() + " cannot be read again", e);
		}
	}

	/** The children a statement answers, read once (until a refresh). */
	@SuppressWarnings("unchecked")
	protected synchronized <T> List<T> children(DBRProgressMonitor monitor, String key, String sql,
	                                            Function<Map<String, Object>, T> make) throws DBException {
		List<?> cached = loaded.get(key);
		if (cached == null) {
			List<T> made = new ArrayList<>();
			if (persisted) { // a new object has none on the node yet
				for (Map<String, Object> each : dataSource.platformRows(monitor, sql)) {
					made.add(make.apply(each));
				}
			}
			cached = made;
			loaded.put(key, cached);
		}
		return (List<T>) cached;
	}

	/** Read again: its own row (after a save, what the node now holds), its children when next expanded. */
	@Override
	public synchronized DBSObject refreshObject(@NotNull DBRProgressMonitor monitor) throws DBException {
		loaded.clear();
		String sql = selfSql();
		if (sql != null && persisted) {
			List<Map<String, Object>> rows = dataSource.platformRows(monitor, sql);
			if (!rows.isEmpty()) {
				row.clear();
				row.putAll(rows.get(0));
				edits.clear();
				creating = false;
			}
		}
		return this;
	}
}
