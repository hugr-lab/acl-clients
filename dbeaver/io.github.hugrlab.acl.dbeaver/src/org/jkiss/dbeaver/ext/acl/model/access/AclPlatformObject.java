package org.jkiss.dbeaver.ext.acl.model.access;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.acl.model.AclDataSource;
import org.jkiss.dbeaver.model.DBPRefreshableObject;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * A node of the Access control branch (spec 007.3): one row of a `platform` view, read only. Its children are read when
 * it is expanded - one statement each - and kept until it is refreshed.
 */
public abstract class AclPlatformObject implements DBSObject, DBPRefreshableObject {
	protected final AclDataSource dataSource;
	private final DBSObject parent;
	protected final Map<String, Object> row;
	private final Map<String, List<?>> loaded = new HashMap<>();

	protected AclPlatformObject(AclDataSource dataSource, DBSObject parent, Map<String, Object> row) {
		this.dataSource = dataSource;
		this.parent = parent;
		this.row = row;
	}

	/** The column that names it. */
	protected abstract String nameKey();

	protected String text(String key) {
		return PlatformValues.text(row.get(key));
	}

	@NotNull
	@Override
	public String getName() {
		String name = text(nameKey());
		return name == null ? "" : name;
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
		return true;
	}

	/** The children a statement answers, read once (until a refresh). */
	@SuppressWarnings("unchecked")
	protected synchronized <T> List<T> children(DBRProgressMonitor monitor, String key, String sql,
	                                            Function<Map<String, Object>, T> make) throws DBException {
		List<?> cached = loaded.get(key);
		if (cached == null) {
			List<T> made = new ArrayList<>();
			for (Map<String, Object> each : dataSource.platformRows(monitor, sql)) {
				made.add(make.apply(each));
			}
			cached = made;
			loaded.put(key, cached);
		}
		return (List<T>) cached;
	}

	@Override
	public synchronized DBSObject refreshObject(@NotNull DBRProgressMonitor monitor) {
		loaded.clear();
		return this;
	}
}
