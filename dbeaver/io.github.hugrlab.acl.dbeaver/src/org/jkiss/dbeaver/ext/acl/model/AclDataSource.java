package org.jkiss.dbeaver.ext.acl.model;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.generic.model.GenericDataSource;
import org.jkiss.dbeaver.ext.generic.model.meta.GenericMetaModel;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.ext.acl.model.access.*;

/**
 * A connection to a node: Generic's, plus what `platform.console_info()` answered at connect (spec 007.2) - read once,
 * on the user's own connection (data mode, no view-as); a principal with no administration is refused it (one
 * recorded denial per connect), and then no console action is shown. Our dialect is passed here - Generic's
 * createDataSourceImpl makes its own, so a connected session used Generic's until 007.2.
 */
public class AclDataSource extends GenericDataSource {
	private static final Log log = Log.getLog(AclDataSource.class);
	static final String CONSOLE_SQL = "SELECT bundles, admin_catalogs, my_views, my_functions FROM platform.console_info()";

	private volatile ConsoleInfo console;

	public AclDataSource(@NotNull DBRProgressMonitor monitor, @NotNull DBPDataSourceContainer container,
	                     @NotNull GenericMetaModel metaModel) throws DBException {
		super(monitor, container, metaModel, new AclSQLDialect());
	}

	@Override
	public void initialize(@NotNull DBRProgressMonitor monitor) throws DBException {
		super.initialize(monitor);
		// a console's copy is prefixed and a view-as one is the role's: neither reads it (nor records a denial)
		DBPConnectionConfiguration configuration = getContainer().getConnectionConfiguration();
		String mode = configuration.getProperty("acl.mode");
		boolean data = mode == null || mode.isEmpty() || "data".equals(mode);
		String viewAs = configuration.getProperty("acl.viewAs");
		console = data && (viewAs == null || viewAs.isEmpty()) ? readConsole(monitor) : null;
	}

	/** What console_info answered at connect; null when it was not read or was refused. */
	@Nullable
	public ConsoleInfo console() {
		return console;
	}

	// --- spec 007.3: the Access control branch - shown only where console_info answered, each folder only where the
	// principal reads its view (the node judges every read again) ------------------------------------------------

	/** Whether the Access control branch is shown: the node answered console_info for this principal. */
	public boolean isAccessControl() {
		return console != null;
	}

	/** Whether the principal reads platform's view (a folder's visibleIf). */
	public boolean shows(String view) {
		return console != null && console.myViews().contains(view);
	}

	/** Whether the principal may call platform's function (a folder's visibleIf). */
	public boolean calls(String function) {
		return console != null && console.myFunctions().contains(function);
	}

	private final java.util.Map<String, List<?>> access = new java.util.HashMap<>();

	@SuppressWarnings("unchecked")
	private synchronized <T> List<T> accessRows(DBRProgressMonitor monitor, String key, String sql,
	                                             java.util.function.Function<java.util.Map<String, Object>, T> make)
	    throws DBException {
		List<?> cached = access.get(key);
		if (cached == null) {
			List<T> made = new java.util.ArrayList<>();
			for (java.util.Map<String, Object> each : platformRows(monitor, sql)) {
				made.add(make.apply(each));
			}
			cached = made;
			access.put(key, cached);
		}
		return (List<T>) cached;
	}

	/** The rows of one platform statement, by lower-case column name. */
	public List<java.util.Map<String, Object>> platformRows(DBRProgressMonitor monitor, String sql) throws DBException {
		List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
		try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Read the access control");
		     JDBCPreparedStatement statement = session.prepareStatement(sql);
		     JDBCResultSet rs = statement.executeQuery()) {
			java.sql.ResultSetMetaData meta = rs.getMetaData();
			while (rs.next()) {
				java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
				for (int i = 1; i <= meta.getColumnCount(); i++) {
					row.put(meta.getColumnLabel(i).toLowerCase(java.util.Locale.ROOT), rs.getObject(i));
				}
				rows.add(row);
			}
		} catch (java.sql.SQLException e) {
			throw new DBException("duckdb-acl: " + e.getMessage(), e);
		}
		return rows;
	}

	@org.jkiss.dbeaver.model.meta.Association
	public List<AclRole> getAccessRoles(DBRProgressMonitor monitor) throws DBException {
		return accessRows(monitor, "roles", PlatformSql.view("roles"), r -> new AclRole(this, this, r));
	}

	@org.jkiss.dbeaver.model.meta.Association
	public List<AclVirtualCatalog> getAccessCatalogs(DBRProgressMonitor monitor) throws DBException {
		return accessRows(monitor, "catalogs", PlatformSql.view("catalogs"), r -> new AclVirtualCatalog(this, this, r));
	}

	/** The physical databases the policy builds over: the first part of every relation's and schema's physical name. */
	@org.jkiss.dbeaver.model.meta.Association
	public synchronized List<AclSource> getAccessSources(DBRProgressMonitor monitor) throws DBException {
		List<?> cached = access.get("sources");
		if (cached == null) {
			java.util.Set<String> names = new java.util.TreeSet<>();
			for (java.util.Map<String, Object> r : platformRows(monitor, PlatformSql.view("relations"))) {
				addSource(names, r.get("phys"));
			}
			for (java.util.Map<String, Object> r : platformRows(monitor, PlatformSql.view("schemas"))) {
				addSource(names, r.get("phys_path"));
			}
			List<AclSource> made = new java.util.ArrayList<>();
			for (String name : names) {
				made.add(new AclSource(this, this, java.util.Map.of("source", name)));
			}
			cached = made;
			access.put("sources", cached);
		}
		@SuppressWarnings("unchecked")
		List<AclSource> sources = (List<AclSource>) cached;
		return sources;
	}

	private static void addSource(java.util.Set<String> names, Object phys) {
		if (phys == null || phys.toString().isEmpty()) {
			return;
		}
		List<String> parts = SchemaPath.parts(phys.toString());
		if (parts != null && !parts.isEmpty()) {
			names.add(parts.get(0));
		}
	}

	@org.jkiss.dbeaver.model.meta.Association
	public List<AclIssuer> getAccessIssuers(DBRProgressMonitor monitor) throws DBException {
		return accessRows(monitor, "issuers", PlatformSql.view("issuers"), r -> new AclIssuer(this, this, r));
	}

	@org.jkiss.dbeaver.model.meta.Association
	public List<AclRoleMapping> getAccessRoleMappings(DBRProgressMonitor monitor) throws DBException {
		return accessRows(monitor, "role_mappings", PlatformSql.view("role_mappings"), r -> new AclRoleMapping(this, this, r));
	}

	@org.jkiss.dbeaver.model.meta.Association
	public List<AclFunctionCategory> getAccessFunctionCategories(DBRProgressMonitor monitor) throws DBException {
		return accessRows(monitor, "function_categories", PlatformSql.view("function_categories"),
		                  r -> new AclFunctionCategory(this, this, r));
	}

	@org.jkiss.dbeaver.model.meta.Association
	public List<AclFunctionGrant> getAccessFunctionGrants(DBRProgressMonitor monitor) throws DBException {
		return accessRows(monitor, "function_grants", PlatformSql.view("function_grants"),
		                  r -> new AclFunctionGrant(this, this, r));
	}

	@org.jkiss.dbeaver.model.meta.Association
	public List<AclResourceGroup> getAccessResourceGroups(DBRProgressMonitor monitor) throws DBException {
		return accessRows(monitor, "resource_groups", PlatformSql.view("resource_groups"),
		                  r -> new AclResourceGroup(this, this, r));
	}

	@Override
	public org.jkiss.dbeaver.model.struct.DBSObject refreshObject(@NotNull DBRProgressMonitor monitor) throws DBException {
		synchronized (this) {
			access.clear();
		}
		return super.refreshObject(monitor);
	}

	private ConsoleInfo readConsole(DBRProgressMonitor monitor) {
		try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Read the console's rights");
		     JDBCPreparedStatement statement = session.prepareStatement(CONSOLE_SQL);
		     JDBCResultSet rs = statement.executeQuery()) {
			if (rs.next()) {
				return new ConsoleInfo(strings(rs.getObject(1)), strings(rs.getObject(2)), strings(rs.getObject(3)),
				                       strings(rs.getObject(4)));
			}
		} catch (Exception e) {
			log.debug("duckdb-acl: no console info (" + e.getMessage() + ")");
		}
		return null;
	}

	/** A VARCHAR[] as the driver answers it (a java.sql.Array, a list or an array) as a set of strings. */
	static Set<String> strings(Object value) throws SQLException {
		Object[] items = value instanceof Array array ? (Object[]) array.getArray()
		                 : value instanceof Collection<?> list   ? list.toArray()
		                 : value instanceof Object[] plain        ? plain
		                                                          : new Object[0];
		Set<String> out = new LinkedHashSet<>();
		for (Object item : items) {
			if (item != null) {
				out.add(item.toString());
			}
		}
		return out;
	}
}
